$.fn.apiSubscriber = function () {
    var self = $(this);

    var showFields = function (event) {
        var $clickedToggle = $(event.target),
            fieldsContainer = $("#" + $clickedToggle.attr("data-fields-container-id"));
        showElement(fieldsContainer); // Show the relative fields container div so that a user can populate custom fields
    };

    var showError = function () {
        self.find(".error-notification").css("display", "block");
        self.find('[type="submit"]').removeAttr('disabled');
    };

    var hideError = function() {
        self.find(".error-notification").css("display", "none");
        self.find('[type="submit"]').removeAttr('disabled');
    };

    var subscribe = function (event) { // subscribes user to api posting a serialized form
        var $form = $(this),
            url = $form.attr("action");

        event.preventDefault();
        $.post(url, $form.serialize(), function (response, textStatus, xhr) {
                subscribeResponseHandler(response, xhr);
            })
            .fail(function () {
                showError();
            });
    };

    var unsubscribe = function (event) { // subscribes user to api posting a serialized form
            var $form = $(this),
                url = $form.attr("action");

            event.preventDefault();
            $.post(url, $form.serialize(), function (response, textStatus, xhr) {
                    unsubscribeResponseHandler(response, xhr);
                })
                .fail(function () {
                    showError();
                });
        };

    var subscribeResponseHandler = function (response, xhr) {
        var updateSubscription = function () {
            $("#subscriptions-" + encodeString(response.apiName) + "-" + encodeString(response.group)).text(response.numberOfSubscriptionText);
            showElement(self.find(".toggle.subscribed"));
            hideElement(self.find(".toggle.not-subscribed"));
            hideError();
        };

        function isSuccess() {
            return /2[0-9]{0,2}/g.test(xhr.status);
        }

        if (isSuccess()) {
            updateSubscription(response);
        } else {
            showError();
        }
    };

    var unsubscribeResponseHandler = function (response, xhr) {
        var updateSubscription = function () {
            $("#subscriptions-" + encodeString(response.apiName) + "-" + encodeString(response.group)).text(response.numberOfSubscriptionText);
            hideElement(self.find(".toggle.subscribed"));
            showElement(self.find(".toggle.not-subscribed"));
            hideError();
        };

        function isSuccess() {
            return /2[0-9]{0,2}/g.test(xhr.status);
        }

        if (isSuccess()) {
            updateSubscription(response);
        } else {
            showError();
        }
    };

    var encodeString = function (s) {
        return s.replace(/[^0-9A-Z]+/gi, '_')
    };

    var hideElement = function (element) {
        element.addClass('hidden');
    };

    var showElement = function (element) {
        element.removeClass('hidden');
    };

    self.find(".has_fields").on("click", showFields);
    self.find(".no-fields-subscription").on("submit", subscribe);
    self.find(".no-fields-unsubscribe").on("submit", unsubscribe);
};

$(document).ready(function () {
    $(".conditionallyHide").each(function () {
        var that = $(this);
        if (that.attr("aria-hidden") && that.attr('aria-hidden') === 'true') {
            that.addClass("hidden");
        }
    });

    $(".api-subscriber").each(function () {
        $(this).apiSubscriber();
    });

    $(".accordion").click(function() {
        var link = $(this);
        setTimeout(function() {
            link.find(".accordion__button").attr("aria-expanded", link.attr("aria-expanded"));
        }, 0);
    });

    var charCount = new GOVUK.CharCount()
    charCount.init({ selector: '.js-char-count' });
});

var showHide = function () {
    var showHideContent = new GOVUK.ShowHideContent();
    showHideContent.init();
};

if (document.addEventListener) {
    document.addEventListener("DOMContentLoaded", function () {
        showHide();
    });
} else {
    window.attachEvent("onload", function () {
        showHide();
    });
}
