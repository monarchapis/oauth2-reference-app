<!-- JS -->
<script src="${pageContext.request.contextPath}/javascripts/jquery-1.11.2.min.js"></script>
<script src="${pageContext.request.contextPath}/javascripts/jquery.blockUI.js"></script>
<script type="text/javascript">
	var failedLogins = <%= new ObjectMapper().writeValueAsString(request.getAttribute("failedLogins")) %>;
	<c:choose>
		<c:when test="${phase != 'invalid-request'}">
			var renderForm = true;
		</c:when>
		<c:otherwise>
			var renderForm = false;
		</c:otherwise>
	</c:choose>
	var contextPath = "${pageContext.request.contextPath}";
	var prefix = "${prefix}";
</script>
<script src="${pageContext.request.contextPath}/javascripts/main.js"></script>
<c:if test="${not empty(gaAccount)}">
	<!-- Google Analytics Code -->
	<script>
		var _gaq = _gaq || [];
		_gaq.push(['_setAccount', '${gaAccount}']);
		_gaq.push(['_trackPageview']);
		_gaq.push(['_setDomainName', 'none']);

		(function() {
			var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;
			ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
			var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);
		})();
	</script>
</c:if>
<c:if test="${empty(gaAccount)}">
	<script>
		var _gaq = _gaq || [];
	</script>
</c:if>