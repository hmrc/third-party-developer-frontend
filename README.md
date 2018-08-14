# Third Party Developer Frontend

## Adding custom local CSS & javascript
Please add all custom javascript to :
* `app/assets/javascripts/custom.js`

To enable custom javascript in the page, please uncomment `@scriptElems` in `app/views/include/main.scala.html`

Please import all custom CSS in to :
* `app/assets/stylesheets/main.scss`

## Unit tests
To run the unit tests:

```
sbt test
```

## Component Tests
Component tests run within a browser to verify the component through various UI flows with mocked backend connections. 

These tests rely on running ASSETS_FRONTEND

From the command line you can run the tests

```
sm --start ASSETS_FRONTEND
sbt component:test
```

To run an individual feature you need to be running the app in stub mode locally and the glue needs to be set as component.steps in the test config.


## ZAP testing
Once the third-party-developer-frontend is running locally, it can be tested using OWASP Zed Attack Proxy (ZAP) which is a security tool that can be used to highlight any potential vulnerabilities: - 
* Download and install [ZAP](https://www.owasp.org/index.php/OWASP_Zed_Attack_Proxy_Project).
* Install the latest scanners for ZAP by `Managing Add-ons` and adding `Active scanner rules (beta)` 
* Ensure your local machine has Web Proxy (HTTP) enabled and local host set to the Port ZAP is running on (e.g.`11000`).
* ZAP can be started to run on a specific port by running `/Applications/OWASP\ ZAP.app/Contents/Java/zap.sh -port 11000` in terminal.

Various security tests can be run within ZAP and the different types of attacks are dependent on the service under test. In order to setup different tests and reporting thresholds: -
* Navigate to the `Scan Policy Manager` within the `Analyse` menu option.
* Within the `Scan Policy Manager` create a new policy and set the different reporting and attack thresholds.
* Providing the proxy settings above are set, ZAP can monitor the local requests when certain user actions are completed.
* Once the request appears in ZAP, right click on it and select `Attack` and `Active Scan`
* Select the policy tab and set the appropriate policy for the service under test.
* Select to start the scan.
* Once the scan is complete the security tests run against the service are displayed in the ZAP interface.
* Reports can also be generated and saved in various formats from the ZAP Report menu option.
* When running Zap tests on third-party-developer-frontend, the following user actions are an example of what can be included in the tests:
  * Register new user
  * Reset password
  * Create Production and Sandbox applications
  * Submit appliction for production credentials
  * Add redirect URI
  * Add team members Admin/Dev
  * Change profile
  * Edit application details
  * Sign out survey


## Testing approach

* Unit tests should make up the majority of tests so that test coverage should drop marginally when run against only unit tests.
* Component tests should be a thin layer of coverage on happy paths only to ensure that journeys hang together.