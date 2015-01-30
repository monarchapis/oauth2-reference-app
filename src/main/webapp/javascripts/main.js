(function($) {
	OAuth = {
		showLoading: function() {
			$.blockUI({
				message: 'Loading...',
				css: {
					border: 'none',
					padding: '15px',
					backgroundColor: '#000',
					'border-radius' : '10px',
					'-webkit-border-radius': '10px',
					'-moz-border-radius': '10px',
					opacity: .5,
					color: '#fff',
					top:  ($(window).height() - 200) / 2 + 'px',
					left: ($(window).width() - 200) / 2 - 15 + 'px',
					width: '200px'
				}
			});
		},
		init: function() {
			if (renderForm && $("#initial-loading-message").size() > 0) {
				OAuth.renderForm();
			} else {
				OAuth.bindEvents();
			}
		},
		renderForm: function() {
			$.ajax({
				url: contextPath + "/authorize/renderForm",
				type: "POST",
				dataType: "html",
				data: {
					popup: opener != null,
					standalone: window.navigator.standalone != null ? window.navigator.standalone : false,
					framed: self != top,
					webview: /(iPhone|iPod|iPad).*AppleWebKit(?!.*Safari)/i.test(navigator.userAgent)
				},
				success: function(data) {
					$("main").html(data);
					$("form :input:visible:first").focus();
					OAuth.bindEvents();
				},
				error: function(obj) {
					var data = eval("(" + obj.responseText + ")");
					if (data.error) {
						$("#formError").text(data.error);
					}
				}
			});
		},
		bindEvents: function() {
			$("main").on("submit", "form", function(ev) {
				ev.preventDefault();

				var form = $(this);
				if (OAuth.validateForm(form)) {
					OAuth.showLoading();
					$.ajax({
						url: this.action,
						type: "POST",
						dataType: "html",
						data: form.serialize(),
						success: function(data) {
							$.unblockUI();
							if ($("#formType").val() != null) {
								var formType = $("#formType").val();
							}

							$("main").html(data);

							// var scrollTop = $(document).scrollTop(),
							//     top = Math.max(0, $("main").offset().top - 15);

							// if (scrollTop > top) {
							//   $("html, body").animate({scrollTop: top}, "slow");
							// }

							$("main button.action").on("click", function() {
								$("main input[name='choice']").val($(this).val());
							});
							
							$("form :input:visible:first").focus();
						},
						error: function(obj) {
							$.unblockUI();
							if ($("#formType").val() != null) {
								var formType = $("#formType").val();
							}

							var data = eval("(" + obj.responseText + ")");
							if (data.error) {
								$("#formError").empty().append(data.error);
								$("form :input:visible:first").focus();
							}
						}
					});
				}
			}).on("click", "button[type='submit']", function() {
				OAuth.validateForm($(this).parents("form"));
			}).on("click", ".return-to-app", function(ev) {
				window.location.href = $(this).data("return-url");
			});
		},
		validateForm: function(form) {
			var valid = true;

			$("input[required]", form).each(function(index, element) {
				var $this = $(this);

				if ($this.val().length == 0) {
					$("#" + element.id + "Error").removeClass("hide");
					$this.parent().addClass("has-error");
				} else {
					$("#" + element.id + "Error").addClass("hide");
					$this.parent().removeClass("has-error");
				}
			});

			return valid;
		}
	};
	
	OAuth.init();
	window.OAuth = OAuth;
})(jQuery);