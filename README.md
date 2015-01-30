OAuth 2 Reference Implementation
================================

This application is the UI for authorizing 3rd party applications to use your developed APIs.

In monarch, a client must have permissions assigned to any of the authorization schemes below:

- oauth2-authorization-code
- oauth2-implicit
- oauth2-password

For demostration purposes, the out of the box configuration uses a properties file for user credentials but provides an LDAP implementation that you can switch to in the Spring context.

The app assumes that the following Java system properties are set in your app server:

- `api.conf.dir` - The API configuration directory (shared)
- `api.logs.dir` - The API logs directory (shared)

Quickstart
----------

Download and extract [Tomcat 8](http://tomcat.apache.org/download-80.cgi "Tomcat 8 download") and create ${TOMCAT_HOME}/bin/setenv.sh (or .bat for Windows) with the following.

```
export CATALINA_OPTS="$CATALINA_OPTS -Dapi.conf.dir=$CATALINA_BASE/conf -Dapi.logs.dir=$CATALINA_BASE/logs"
export ENC_PWD="1qK6CHCkyhpzJHJuNhgVFzpc"
```

Then add the following user in ${TOMCAT_HOME}/conf/tomcat-users.xml.  This will allow you to deploy the app to Tomcat from Maven.

```
<user name="admin" password="admin" roles="standard,manager-script" />
```

For your convenience, the files in the /conf directory of this project can be copied to the ${TOMCAT_HOME}/conf directory.  They are configured for a simple Monarch setup (Everything running on localhost, Standalone, no SSL, no MongoDB authentication).

To build and deploy, run `mvn clean tomcat:redeploy`

Now, you will need to create the following in the Monarch admin console:

- **Environment**
	- Name = demo
	- System Database = demo
	- Analytics Database = demo

- **Access**
	- **Provider**
		- Name = oauth
		- API Key = x3TPDTnMiaC9aV86iEdeFGb0
		- Shared Secret = GxKOijlc4tjZVH2VJVO82rh7
		- Service Permissions:
			- Delegate access
			- Revoke access
		- Authenticators:
			- Hawk V1
				- SHA-256
				- Require payload validation checked