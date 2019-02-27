$(document).ready(function() {
  // Copy To Clipboard
  var copyButtons = $('.copy-to-clip');

  function copyTextToClipboard(text) {
    var textArea = document.createElement("textarea");
    textArea.style.position = 'fixed';
    textArea.style.top = 0;
    textArea.style.left = 0;
    textArea.style.width = '2em';
    textArea.style.height = '2em';
    textArea.style.padding = 0;
    textArea.style.border = 'none';
    textArea.style.outline = 'none';
    textArea.style.boxShadow = 'none';
    textArea.style.background = 'transparent';
    textArea.value = text;

    document.body.appendChild(textArea);

    textArea.select();

    try {
      var successful = document.execCommand('copy');
    } catch (e) {
      // allow failure - still want to remove textArea
      // test if we should even display the button later
    }

    document.body.removeChild(textArea);
  }

  copyButtons.each(function(index) {
    var self = $(this);
    var ariaLabel = self.attr('aria-label');

    self.on('click', function(e) {
      e.preventDefault();
      e.stopPropagation();

      copyTextToClipboard(self.data('clip-text') );
      self.attr('aria-label', ariaLabel.replace('Copy', 'Copied'));
      self.focus();

      setTimeout(function() {
        self.attr('aria-label', ariaLabel);
      }, 2000);
    });
  });
});
