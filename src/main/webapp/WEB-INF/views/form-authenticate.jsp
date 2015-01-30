<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://www.springframework.org/tags/form" prefix="form" %>

<form id="authenticateForm" role="form" action="${pageContext.request.contextPath}/${prefix}/authenticate" method="post" onsubmit="return false;" autocomplete="off">
	<h1>Login to your account</h1>
	<p id="formError" class="error">
		<c:if test="${not empty(message)}">
			<c:out value="${message}" />
		</c:if>
	</p>
	<div id="usernameGroup" class="form-group">
		<div id="usernameError" class="error hide">Please enter your Username</div>
		<label class="sr-only" for="username">Username</label>
		<input type="text" class="form-control" name="username" id="username" placeholder="Username" title="Please enter your username" maxlength="32" spellcheck="false" autocomplete="off" autocapitalize="off" autocorrect="off" autofocus="autofocus" required="required" />
	</div>
	<div id="passwordGroup" class="form-group">
		<div id="passwordError" class="error hide">Please enter your Password</div>
		<label class="sr-only" for="password">Password</label>
		<input type="password" class="form-control" name="password" id="password" placeholder="Password" title="Please enter your password" maxlength="32" autocomplete="off" required="required" />
	</div>
	<input type="hidden" id="formType" name="formType" value="Authenticate" />
	<button id="btn-login" type="submit" class="btn btn-primary btn-block">Login</button>
	<button id="btn-cancel" type="button" class="return-to-app btn btn-default btn-block" data-return-url="${returnUrl}">Cancel</button>
</form>