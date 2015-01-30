<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://www.springframework.org/tags/form" prefix="form" %>

<div class="monarch-container">
	<h4>There was an error in the request.</h4>
	<c:if test="${!empty(returnUrl)}">
	<button value="return" class="return-to-app btn btn-primary" data-return-url="${returnUrl}" onclick="_gaq.push(['_trackEvent', 'ReturnToApp', 'Back']);">Return to application</button>
	</c:if>
</div>