package com.oriole.wisepen.document.util;

import com.google.zxing.common.reedsolomon.GenericGF;
import com.google.zxing.common.reedsolomon.ReedSolomonEncoder;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * 微型点阵暗水印编解码器（生成端）。
 *
 * <h3>Tile 布局（128×128 px 灰度图）</h3>
 * <pre>
 * (0,0)────────────────────────────(127,0)
 *   ╔════════╗                ╔════════╗
 *   ║Anchor A║                ║Anchor B║  ← 8×8 solid black，位于四角
 *   ╚════════╝                ╚════════╝
 *
 *          ·  ·  ·  ·  ·  ·  ·  ·         ← 数据点，2×2 px 纯黑（bit=1）或白（bit=0）
 *            数据区 (16,16)–(111,111)         16 列 × 16 行 = 256 cells（6px pitch）
 *
 *   ╔════════╗                ╔════════╗
 *   ║Anchor C║                ║Anchor D║  ← D 带 4×4 白色内洞（定向标记）
 *   ╚════════╝                ╚════════╝
 * (0,127)──────────────────────────(127,127)
 * </pre>
 *
 * <h3>编码流程</h3>
 * <ol>
 *   <li>将 userId 的 UTF-8 字节截断或零填充至 16 字节。</li>
 *   <li>AES-128-ECB 加密，得到 16 字节密文（payload）。</li>
 *   <li>ZXing ReedSolomonEncoder（GF(2^8), 0x011d）编码为 RS(32,16) codeword（32 字节 = 256 bits）。</li>
 *   <li>按 row-major 顺序将 256 bits 映射到数据区 16×16 网格，bit=1 → 2×2 黑点，bit=0 → 留白。</li>
 *   <li>四角绘制定位锚点：A/B/C 为 8×8 solid black；D（右下）为带 4×4 白色内洞的环形。</li>
 * </ol>
 *
 * <h3>提取</h3>
 * 提取逻辑由独立 Python 脚本完成（见 extract_watermark.py）：
 * 任意截图/手机照片 → CLAHE增强 → 自适应二值化 → SimpleBlobDetector 锚点检测 →
 * 四边形重建 → warpPerspective 透视校正 → 采样数据区 → reedsolo RS 解码 → AES 解密。
 *
 */
public final class MicroDotCodec {

    // -------------------------------------------------------------------------
    //  Tile 结构常量（Python 提取脚本必须与之同步）
    // -------------------------------------------------------------------------

    /** Tile 像素边长（正方形） */
    public static final int TILE_SIZE = 128;

    /**
     * 参考 DPI，决定 Tile 在 PDF 页面中的物理尺寸。
     * tile_pt = TILE_SIZE * 72 / REF_DPI = 128 * 72 / 150 = 61.44 pt
     */
    public static final float REF_DPI = 150f;

    /** Tile 在 PDF 坐标系中的边长（点，1pt = 1/72 inch） */
    public static final float TILE_PT = TILE_SIZE * 72f / REF_DPI; // 61.44

    /** 定位锚点的像素边长 */
    public static final int ANCHOR_SIZE = 8;

    /**
     * 锚点在 Tile 四角的起始坐标（左上为原点）。
     * 右侧/底部锚点起始坐标：TILE_SIZE - ANCHOR_SIZE = 120。
     */
    public static final int ANCHOR_OFFSET_FAR = TILE_SIZE - ANCHOR_SIZE; // 120

    /** 数据区起始坐标（x 和 y 相同，数据区是正方形） */
    public static final int DATA_ORIGIN = 16;

    /** 数据网格的列/行数（16×16 = 256 cells） */
    public static final int DATA_GRID = 16;

    /** 相邻数据点的像素步进 */
    public static final int DATA_PITCH = 6;

    /** 每个数据点的像素边长（2×2） */
    public static final int DOT_SIZE = 2;

    /** 数据点在单元内的偏移（使点居中于 6×6 单元中的位置 2,2） */
    public static final int DOT_OFFSET = 2;

    /** RS(32,16) codeword 总字节数（= 数据区总 bit 数 / 8） */
    public static final int RS_TOTAL = 32;

    /** RS 原始数据字节数（= AES-128 payload 字节数） */
    public static final int RS_DATA = 16;

    /** Anchor D 内洞的像素边长 */
    private static final int ANCHOR_D_HOLE = 4;

    /** 背景色（纯白） */
    private static final byte BG = (byte) 255;

    /** 数据点/锚点颜色（纯黑） */
    private static final byte INK = (byte) 0;

    // -------------------------------------------------------------------------
    //  公共 API
    // -------------------------------------------------------------------------

    /**
     * 将 userId 加密、RS 编码后渲染为 {@value #TILE_SIZE}×{@value #TILE_SIZE}
     * 原始灰度字节数组（{@value #TILE_SIZE}² = 16384 字节）。
     *
     * <p>此格式可直接作为 PDF Image XObject 的 Raw（无压缩）流数据写入文件。
     *
     * @param userId    要嵌入的用户标识
     * @param aesKeyB64 Base64 编码的 AES-128 密钥（解码后恰好 16 字节）
     * @return 16384 字节的原始灰度像素数据
     */
    public static byte[] buildRawTileBytes(String userId, String aesKeyB64) {
        byte[] payload = encrypt(userId, decodeKey(aesKeyB64));
        int[] codeword = rsEncode(payload);
        return renderTile(codeword);
    }

    // -------------------------------------------------------------------------
    //  内部：编码管线
    // -------------------------------------------------------------------------

    /**
     * AES-128-ECB 加密 userId，返回 16 字节密文。
     */
    private static byte[] encrypt(String userId, byte[] key) {
        byte[] padded = Arrays.copyOf(userId.getBytes(StandardCharsets.UTF_8), RS_DATA);
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(padded);
        } catch (Exception e) {
            throw new IllegalStateException("微型点阵水印加密失败", e);
        }
    }

    /**
     * RS(32,16) over GF(2^8) 编码。
     * 使用 ZXing 的 {@link GenericGF#QR_CODE_FIELD_256}（本原多项式 0x011d）。
     *
     * @param payload 16 字节 AES 密文
     * @return 32 个 int 的 codeword 数组（前 16 为数据，后 16 为 EC 符号，每个值均在 0–255）
     */
    private static int[] rsEncode(byte[] payload) {
        int[] codeword = new int[RS_TOTAL];
        for (int i = 0; i < RS_DATA; i++) {
            codeword[i] = payload[i] & 0xFF;
        }
        new ReedSolomonEncoder(GenericGF.QR_CODE_FIELD_256).encode(codeword, RS_TOTAL - RS_DATA);
        return codeword;
    }

    // -------------------------------------------------------------------------
    //  内部：Tile 渲染
    // -------------------------------------------------------------------------

    /**
     * 将 32-byte codeword 渲染为 128×128 原始灰度字节数组。
     *
     * <p>布局：
     * <ul>
     *   <li>全图背景：白色（255）</li>
     *   <li>Anchor A（左上）：(0,0)–(7,7) solid black</li>
     *   <li>Anchor B（右上）：(120,0)–(127,7) solid black</li>
     *   <li>Anchor C（左下）：(0,120)–(7,127) solid black</li>
     *   <li>Anchor D（右下，定向）：(120,120)–(127,127) 8×8 黑环，中心 4×4 白洞</li>
     *   <li>数据区 (16,16)–(111,111)：256 个 2×2 px 黑点，bit=1 涂黑，bit=0 留白</li>
     * </ul>
     */
    private static byte[] renderTile(int[] codeword) {
        byte[] pixels = new byte[TILE_SIZE * TILE_SIZE];
        Arrays.fill(pixels, BG);

        drawAnchorSolid(pixels, 0, 0);
        drawAnchorSolid(pixels, ANCHOR_OFFSET_FAR, 0);
        drawAnchorSolid(pixels, 0, ANCHOR_OFFSET_FAR);
        drawAnchorWithHole(pixels, ANCHOR_OFFSET_FAR, ANCHOR_OFFSET_FAR);

        // codeword → bit stream（row-major，MSB first）→ 数据点
        int bitIndex = 0;
        for (int row = 0; row < DATA_GRID; row++) {
            for (int col = 0; col < DATA_GRID; col++) {
                int byteIdx = bitIndex / 8;
                int bitPos  = 7 - (bitIndex % 8);
                boolean bit = ((codeword[byteIdx] >> bitPos) & 1) == 1;
                if (bit) {
                    int dotX = DATA_ORIGIN + col * DATA_PITCH + DOT_OFFSET;
                    int dotY = DATA_ORIGIN + row * DATA_PITCH + DOT_OFFSET;
                    for (int dy = 0; dy < DOT_SIZE; dy++) {
                        for (int dx = 0; dx < DOT_SIZE; dx++) {
                            pixels[(dotY + dy) * TILE_SIZE + (dotX + dx)] = INK;
                        }
                    }
                }
                bitIndex++;
            }
        }

        return pixels;
    }

    /** 绘制 {@value #ANCHOR_SIZE}×{@value #ANCHOR_SIZE} 实心黑方块。 */
    private static void drawAnchorSolid(byte[] pixels, int x0, int y0) {
        for (int dy = 0; dy < ANCHOR_SIZE; dy++) {
            for (int dx = 0; dx < ANCHOR_SIZE; dx++) {
                pixels[(y0 + dy) * TILE_SIZE + (x0 + dx)] = INK;
            }
        }
    }

    /**
     * 绘制定向锚点 D：8×8 黑色外框 + 4×4 白色内洞（环形标记）。
     * Python 通过检测 blob 内部的白色空洞来识别此锚点，确定 Tile 方向。
     */
    private static void drawAnchorWithHole(byte[] pixels, int x0, int y0) {
        drawAnchorSolid(pixels, x0, y0);
        int holeOffset = (ANCHOR_SIZE - ANCHOR_D_HOLE) / 2;
        int hx = x0 + holeOffset;
        int hy = y0 + holeOffset;
        for (int dy = 0; dy < ANCHOR_D_HOLE; dy++) {
            for (int dx = 0; dx < ANCHOR_D_HOLE; dx++) {
                pixels[(hy + dy) * TILE_SIZE + (hx + dx)] = BG;
            }
        }
    }

    // -------------------------------------------------------------------------
    //  内部：密钥处理
    // -------------------------------------------------------------------------

    private static byte[] decodeKey(String base64Key) {
        byte[] key = Base64.getDecoder().decode(base64Key);
        return Arrays.copyOf(key, RS_DATA);
    }
}
