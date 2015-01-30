<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://www.springframework.org/tags/form" prefix="form" %>

<div class="row vertical-divider">
	<div id="requestContainer" class="column">
		<h1>Permissions Request</h1>
		<p><strong><c:out value="${application.name}" /></strong> is requesting to connect to your account in order&nbsp;to:
		<c:if test="${not empty(permissions)}">
			<ul>
				<c:forEach items="${permissions}" var="message">
					<c:set var="message" value="${message}" scope="request" />
					<jsp:include page="message-node.jsp" />
				</c:forEach>
			</ul>
		</c:if>
		<p class="error">Authorizing this request will not give <c:out value="${application.name}" /> access to any other information about you or your account.</p>
		<p class="error"><strong>
			<c:choose>
				<c:when test="${not empty(formattedPeriod)}">Access will be active for the next 
					<span class="nowrap"><c:out value="${formattedPeriod}" /></span>.
				</c:when>
				<c:otherwise>It will be active until you decide to revoke the applications privileges.</c:otherwise>
			</c:choose>
		</strong></p>
		<div id="authControls">
			<h2>Do you authorize <c:out value="${application.name}" /> with the above&nbsp;permissions?</h2>
			<form id="authorizeForm" role="form" action="${pageContext.request.contextPath}/${prefix}/authorize" method="post">
				<input type="hidden" name="choice" value="">
				<button type="button" class="action return-to-app btn btn-default" value="declined" data-return-url="${returnUrl}">Cancel</button>
				<button type="submit" class="action btn btn-success" value="accepted">Authorize</button>
			</form>
		</div>
	</div>
	<div id="applicationContainer" class="column">
		<c:if test="${not empty(application.applicationImageUrl)}">
			<p class="text-center app-logo"><img id="applicationLogo" class="img-responsize" src="${application.applicationImageUrl}" alt="${application.name} Logo" /></p>
		</c:if>
		<c:if test="${not empty(application.companyName) || not empty(application.name)}">
			<c:if test="${not empty(application.name)}">
				<h3><c:out value="${application.name}" /></h3>
			</c:if>
			<c:if test="${not empty(application.companyName) && application.companyName != application.name}">
				<h5>by <c:out value="${application.companyName}" /></h5>
			</c:if>
		</c:if>
		<c:if test="${not empty(application.description)}">
			<p><c:out value="${application.description}" /></p>
		</c:if>
		<div id="monarchInfo">
			<h4>What's going on here?</h4>
			<p>Monarch is an API platform, developed by CapTech Ventures, Inc., that enables companies to open their APIs to the public.</p>
			<c:if test="${application.companyName != 'Company Name, Inc.'}">
			<p id="disclaimer">Company Name is not affiliated with <c:choose><c:when test="${not empty(application.companyName)}"><c:out value="${application.companyName}" /></c:when><c:otherwise><c:out value="${application.name}" /></c:otherwise></c:choose>. You should allow access only if you trust this application with the information listed on the left.
			</c:if>
		</div>
	</div>
</div>