<%@ page import="org.pegdown.PegDownProcessor, com.monarchapis.api.v1.model.MessageDetails" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://www.springframework.org/tags/form" prefix="form" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<li>
	<c:choose>
		<c:when test="${message.format == 'text'}">
			<c:out value="${message.content}" /></li>
		</c:when>
		<c:when test="${message.format == 'markdown'}">
			<%
			MessageDetails message = (MessageDetails) request.getAttribute("message");
			PegDownProcessor pdp = new PegDownProcessor();
			String mdHtml = pdp.markdownToHtml(message.getContent());
			out.print(mdHtml);
			%>
		</c:when>
		<c:when test="${message.format == 'html'}">
			${message.content}
		</c:when>
	</c:choose>
	<c:if test="${not empty message.children}">
		<ul>
			<c:forEach items="${message.children}" var="message">
				<c:set var="message" value="${message}" scope="request" />
				<jsp:include page="message-node.jsp" />
			</c:forEach>
		</ul>
	</c:if>
</li>