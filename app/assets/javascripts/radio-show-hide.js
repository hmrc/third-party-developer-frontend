(function () {
  function showInput() {
    var radios = document.getElementsByName('hasUrl');
    for (var i = 0, length = radios.length; i < length; i++) {
      if (radios[i].checked) {
        var status = radios[i].value;
        if (status == "true") {
          var id = document.getElementsByClassName("show-hide")[0].id;
          var yesRadio = document.getElementById(id);
          yesRadio.classList.remove("js-hidden");
        } else {
          var id = document.getElementsByClassName("show-hide")[0].id;
          var yesRadio = document.getElementById(id);
          yesRadio.classList.add("js-hidden");
        }
        break;
      }
    }
  }
  document.getElementById("hasUrl").addEventListener("click", showInput);
})();