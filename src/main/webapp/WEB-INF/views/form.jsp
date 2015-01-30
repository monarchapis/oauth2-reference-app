<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://www.springframework.org/tags/form" prefix="form" %>

<c:choose>
	<c:when test="${phase == 'invalid-request'}">
		<%@ include file="form-invalid-request.jsp" %>
	</c:when>
	<c:when test="${phase == 'authenticate'}">
		<%@ include file="form-authenticate.jsp" %>
	</c:when>
	<c:when test="${phase == 'authorize'}">
		<%@ include file="form-authorize.jsp" %>
	</c:when>
	<c:otherwise>
		<%@ include file="form-loading.jsp" %>
	</c:otherwise>
</c:choose>