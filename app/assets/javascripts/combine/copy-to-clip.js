$(document).ready(function() {
  // Copy To Clipboard
  var copyButtons = $('.copy-to-clip');

  function copyToClipboard(textToCopy) {
    navigator.clipboard.writeText(textToCopy);
  }

  copyButtons.each(function(index) {
    var self = $(this);
    var ariaLabel = self.attr('aria-label');

    self.on('click', function(e) {
      e.preventDefault();
      e.stopPropagation();

      copyToClipboard(self.data('clip-text') );
      self.attr('aria-label', ariaLabel.replace('Copy', 'Copied'));
      self.focus();

      setTimeout(function() {
        self.attr('aria-label', ariaLabel);
      }, 2000);
    });
  });
});
