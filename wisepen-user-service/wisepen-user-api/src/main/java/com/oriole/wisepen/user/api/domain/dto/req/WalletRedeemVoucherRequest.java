package com.oriole.wisepen.user.api.domain.dto.req;

import com.oriole.wisepen.user.api.constant.GroupValidationMsg;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class WalletRedeemVoucherRequest implements Serializable {
	@NotBlank(message = "兑换码不能为空")
	private String voucherCode;
}
