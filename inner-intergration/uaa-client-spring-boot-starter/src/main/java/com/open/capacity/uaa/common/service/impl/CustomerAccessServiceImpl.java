package com.open.capacity.uaa.common.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.util.AntPathMatcher;

import com.open.capacity.common.context.TenantContextHolder;
import com.open.capacity.common.model.SysMenu;
import com.open.capacity.common.properties.SecurityProperties;
import com.open.capacity.uaa.common.service.IFeatureUserService;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 请求权限判断service
 *
 * @author someday
 * @date 2018/10/28 
 * code: https://gitee.com/owenwangwen/open-capacity-platform
 */
@Slf4j
public abstract class CustomerAccessServiceImpl {

	@Autowired
	private SecurityProperties securityProperties;

	@Autowired
	private IFeatureUserService featureUserService;

	private final AntPathMatcher antPathMatcher = new AntPathMatcher();

	/**
	 * 查询当前用户拥有的资源权限
	 * 
	 * @param roleCodes 角色code列表，多个以','隔开
	 * @return
	 */
	public abstract List<SysMenu> findMenuByRoleCodes(String roleCodes);

	public boolean hasPermission(Authentication authentication, String requestMethod, String requestURI) {
		// 前端跨域OPTIONS请求预检放行 也可通过前端配置代理实现
		if (HttpMethod.OPTIONS.name().equalsIgnoreCase(requestMethod)) {
			return true;
		}
		if (!(authentication instanceof AnonymousAuthenticationToken)) {
			// 判断是否开启url权限验证
			if (!securityProperties.getAuth().getUrlPermission().getEnable()) {
				return true;
			}
			// 特权工号不需要校验
			if (featureUserService.isActive()) {
				return true;
			}

			OAuth2Authentication auth2Authentication = (OAuth2Authentication) authentication;
			// 判断应用黑白名单
			if (!isNeedAuth(auth2Authentication.getOAuth2Request().getClientId())) {
				return true;
			}

			// 判断不进行url权限认证的api，所有已登录用户都能访问的url
			for (String path : securityProperties.getAuth().getUrlPermission().getIgnoreUrls()) {
				if (antPathMatcher.match(path, requestURI)) {
					return true;
				}
			}

			List<SimpleGrantedAuthority> grantedAuthorityList = (List<SimpleGrantedAuthority>) authentication
					.getAuthorities();
			if (CollectionUtil.isEmpty(grantedAuthorityList)) {
				log.warn("角色列表为空：{}", authentication.getPrincipal());
				return false;
			}

			// 保存租户信息
			String clientId = auth2Authentication.getOAuth2Request().getClientId();
			TenantContextHolder.setTenant(clientId);

			String roleCodes = grantedAuthorityList.stream().map(SimpleGrantedAuthority::getAuthority)
					.collect(Collectors.joining(", "));
			List<SysMenu> menuList = findMenuByRoleCodes(roleCodes);
			for (SysMenu menu : menuList) {
				if (StringUtils.isNotEmpty(menu.getUrl()) && antPathMatcher.match(menu.getUrl(), requestURI)) {
					if (StrUtil.isNotEmpty(menu.getPathMethod())) {
						return requestMethod.equalsIgnoreCase(menu.getPathMethod());
					} else {
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * 判断应用是否满足白名单和黑名单的过滤逻辑
	 * zlt
	 * @param clientId 应用id
	 * @return true(需要认证)，false(不需要认证)
	 */
	private boolean isNeedAuth(String clientId) {
		boolean result = true;
		// 白名单
		List<String> includeClientIds = securityProperties.getAuth().getUrlPermission().getIncludeClientIds();
		// 黑名单
		List<String> exclusiveClientIds = securityProperties.getAuth().getUrlPermission().getExclusiveClientIds();
		if (includeClientIds.size() > 0) {
			result = includeClientIds.contains(clientId);
		} else if (exclusiveClientIds.size() > 0) {
			result = !exclusiveClientIds.contains(clientId);
		}
		return result;
	}
}
