package com.oriole.wisepen.common.web.interceptor;

import com.alibaba.nacos.common.utils.StringUtils;
import com.oriole.wisepen.common.core.constant.CommonConstants;
import com.oriole.wisepen.common.core.constant.SecurityConstants;
import com.oriole.wisepen.common.core.context.GrayContextHolder;
import feign.RequestInterceptor;
import feign.RequestTemplate;

public class FeignRequestInterceptor implements RequestInterceptor {

    private final String fromSource;

    public FeignRequestInterceptor(String fromSource) {
        this.fromSource = fromSource;
    }

    @Override
    public void apply(RequestTemplate template) {
        template.header(SecurityConstants.HEADER_FROM_SOURCE, fromSource);

        // 透传灰度发布标记
        String developerTag = GrayContextHolder.getDeveloperTag();
        if (StringUtils.hasText(developerTag)) {
            template.header(CommonConstants.GRAY_HEADER_DEV_KEY, developerTag);
        }
    }
}