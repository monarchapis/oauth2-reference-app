<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://www.springframework.org/tags/form" prefix="form" %>

<div class="monarch-container">
	<h2 class="text-center">Access has been granted!</h2>

	<c:if test="${not empty(permissions)}">
		<p>You have allowed the <strong><c:out value="${application.name}" /></strong> the following permissions:</p>
		<ul>
			<c:forEach items="${permissions}" var="message">
				<c:set var="message" value="${message}" scope="request"/>
				<jsp:include page="message-node.jsp"/>
			</c:forEach>
		</ul>
	</c:if>

	<p class="text-center" id="redirect">You will now be redirected back to <c:out value="${application.name}" />&hellip;</p>
</div>
<script type="text/javascript">
	window.setTimeout(function() {
		window.location.href = "${redirectUrl}";
	}, 1000);
</script>