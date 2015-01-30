<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://www.springframework.org/tags/form" prefix="form" %>

<div class="monarch-container">
	<h2>Process Aborted</h2>
	<p>You have declined access to <strong><c:out value="${application.name}" /></strong>.</p>
	<a href="${redirectUrl}" class="btn btn-primary">Return to <c:out value="${application.name}" /></a>
</div>