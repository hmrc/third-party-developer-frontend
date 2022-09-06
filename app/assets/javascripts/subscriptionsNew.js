(function () {
  function subscribe(event) {
    var input = this;
    var form = this.form;

    const XHR = new XMLHttpRequest();
    const FD = new FormData(form);
    const urlEncodedDataPairs = [];

    for (const [name, value] of FD.entries()) {
      urlEncodedDataPairs.push(`${encodeURIComponent(name)}=${encodeURIComponent(value)}`);
    }

    const urlEncodedData = urlEncodedDataPairs.join('&').replace(/%20/g, '+');

    XHR.addEventListener("load", (event) => {
      if(XHR.status == 200) {
        const offButton = input.id.substr(0, input.id.length-2) + 'off';

        document.getElementById(input.id).checked = true;
        document.getElementById(offButton).checked = false;
      }
    });

    XHR.addEventListener("error", (event) => {
      console.log('Oops! Something went wrong.');
    });

    XHR.open("POST", form.action);

    XHR.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');

    XHR.send(urlEncodedData);
  }
  
  function unsubscribe() {
    var input = this;
    var form = this.form;

    const XHR = new XMLHttpRequest();
    const FD = new FormData(form);
    const urlEncodedDataPairs = [];

    for (const [name, value] of FD.entries()) {
      urlEncodedDataPairs.push(`${encodeURIComponent(name)}=${encodeURIComponent(value)}`);
    }

    const urlEncodedData = urlEncodedDataPairs.join('&').replace(/%20/g, '+');

    XHR.addEventListener("load", (event) => {
      if(XHR.status == 200) {
        const nButton = input.id.substr(0, input.id.length-3) + 'on';

        document.getElementById(input.id).checked = true;
        document.getElementById(offButton).checked = false;
      }
    });

    XHR.addEventListener("error", (event) => {
      console.log('Oops! Something went wrong.');
    });

    XHR.open("POST", form.action);

    XHR.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');

    XHR.send(urlEncodedData);
  }

  document.querySelectorAll(".slider__on").forEach(slider => {
    slider.addEventListener("click", subscribe);
  });

  document.querySelectorAll(".slider__off").forEach(slider => {
    slider.addEventListener("click", unsubscribe);
  });
})();
