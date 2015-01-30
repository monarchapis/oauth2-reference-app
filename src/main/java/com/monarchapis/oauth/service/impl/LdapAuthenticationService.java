package com.monarchapis.oauth.service.impl;

import static org.apache.commons.lang3.StringUtils.*;
import static org.apache.commons.lang3.Validate.*;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.monarchapis.oauth.model.LoginResult;
import com.monarchapis.oauth.service.AuthenticationService;

public class LdapAuthenticationService implements AuthenticationService {
	private static final Logger logger = LoggerFactory.getLogger(LdapAuthenticationService.class);

	private String ldapURL;
	private String authMethod = "DIGEST-MD5";
	private String userDN = "{0}";
	private String uidAttribute = "sAMAccountName";
	private String baseDN;
	private boolean useSSL = false;

	private List<String> groupList;

	@Override
	public LoginResult authenticate(String username, String password) {
		notBlank(ldapURL, "ldapURL is required");
		notBlank(userDN, "userDN is required");
		notBlank(authMethod, "authMethod is required");

		// Set up the environment for creating the initial context
		Hashtable<String, String> env = new Hashtable<String, String>();
		env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
		env.put(Context.PROVIDER_URL, ldapURL);

		// Specify SSL
		if (useSSL) {
			env.put(Context.SECURITY_PROTOCOL, "ssl");
		}

		// Authenticate as S. User and password
		env.put(Context.SECURITY_AUTHENTICATION, authMethod);
		String principalDN = MessageFormat.format(userDN, username);

		env.put(Context.SECURITY_PRINCIPAL, principalDN);
		env.put(Context.SECURITY_CREDENTIALS, password);

		// Create the initial context
		try {
			InitialDirContext ctx = new InitialDirContext(env);

			if (inValidGroup(ctx, username)) {
				return LoginResult.SUCCESS;
			} else {
				return LoginResult.FAILURE;
			}
		} catch (AuthenticationException ae) {
			logger.debug("Authentication failed for user {}", username);
			return LoginResult.FAILURE;
		} catch (Exception e) {
			logger.debug("Failure authenticating user {}", username, e);
			return LoginResult.SYSTEM_UNAVAILABLE;
		}
	}

	public boolean inValidGroup(InitialDirContext ctx, String username) throws NamingException {
		if ((groupList == null) || (groupList.isEmpty())) {
			return true;
		}

		notBlank(baseDN, "baseDN is required");
		notBlank(uidAttribute, "uidAttribute is required");

		for (String group : groupList) {
			logger.debug("Testing recursively for group {0}", group);
			String filter = "(&(" + uidAttribute + "={0})(memberOf:1.2.840.113556.1.4.1941:={1}))";
			String searchURL = ldapURL + "/" + baseDN;
			SearchControls ctls = new SearchControls();
			ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
			NamingEnumeration<SearchResult> answer = ctx.search(searchURL, filter, new String[] { username, group },
					ctls);

			if (answer.hasMore()) {
				logger.debug("User {} successfully authenticated based on group {}", username, group);
				return true;
			}
		}

		return false;
	}

	public void setLdapURL(String ldapURL) {
		this.ldapURL = trimToNull(ldapURL);
	}

	public void setAuthMethod(String authMethod) {
		this.authMethod = trimToNull(authMethod);
	}

	public void setUserDN(String userDN) {
		this.userDN = trimToNull(userDN);
	}

	public void setUidAttribute(String uidAttribute) {
		this.uidAttribute = trimToNull(uidAttribute);
	}

	public void setBaseDN(String baseDN) {
		this.baseDN = trimToNull(baseDN);
	}

	public boolean isUseSSL() {
		return useSSL;
	}

	public void setUseSSL(boolean useSSL) {
		this.useSSL = useSSL;
	}

	public void setGroupList(List<String> groupList) {
		this.groupList = scrubList(groupList);
	}

	public void setGroupListAsString(String groups) {
		this.groupList = readList(groups);
	}

	private static List<String> readList(String listAsString) {
		String[] parts = StringUtils.split(listAsString, "|\n\r");
		List<String> list = new ArrayList<String>(parts.length);

		for (String part : parts) {
			String value = StringUtils.trimToNull(part);

			if (value != null) {
				list.add(value);
			}
		}

		return list;
	}

	private static List<String> scrubList(List<String> list) {
		List<String> scrubbedList = new ArrayList<String>(list.size());

		for (String item : list) {
			String value = StringUtils.trimToNull(item);

			if (value != null) {
				scrubbedList.add(value);
			}
		}

		return scrubbedList;
	}
}
