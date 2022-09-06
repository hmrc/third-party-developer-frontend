(function () {
  function getParents(elem) {
    var parents = [];

    while(elem.parentNode && elem.parentNode.nodeName.toLowerCase() != 'body') {
      elem = elem.parentNode;
      parents.push(elem);
    }

    return parents;
  }
  
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

        const topLevelElement = getParents(form).find((node) => node.id.endsWith("-accordian-section"));
        const test = topLevelElement.querySelector('.subscription-count');
        const count = test.innerText.substr(0, test.innerText.indexOf(' '));
        const newCount = parseInt(count) + 1;
        const subscription = newCount === 1 ? 'subscription' : 'subscriptions';
        test.innerText = `${newCount} ${subscription}`;
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
        const onButton = input.id.substr(0, input.id.length-3) + 'on';

        document.getElementById(input.id).checked = true;
        document.getElementById(onButton).checked = false;

        const topLevelElement = getParents(form).find((node) => node.id.endsWith("-accordian-section"));
        const test = topLevelElement.querySelector('.subscription-count');
        const count = test.innerText.substr(0, test.innerText.indexOf(' '));
        const newCount = parseInt(count) - 1;
        const subscription = newCount === 1 ? 'subscription' : 'subscriptions';
        test.innerText = `${newCount} ${subscription}`;
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
