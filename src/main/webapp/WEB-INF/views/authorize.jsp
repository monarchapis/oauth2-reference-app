<%@ include file="inc-top.jsp" %>
<%@ include file="inc-header.jsp" %>
<main class="container">
	<c:choose>
		<c:when test="${phase == 'invalid-request'}">
			<%@ include file="form-invalid-request.jsp" %>
		</c:when>
		<c:when test="${phase == 'internal-error'}">
			<%@ include file="form-internal-error.jsp" %>
		</c:when>
		<c:otherwise>
			<%@ include file="form-loading.jsp" %>
		</c:otherwise>
	</c:choose>
</main>
<%@ include file="inc-footer.jsp" %>
<%@ include file="inc-scripts.jsp" %>
<%@ include file="inc-bottom.jsp" %>