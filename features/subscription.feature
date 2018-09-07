@API-694 @API-691 @API-693 @API-2525
Feature: View/add/delete Subscriptions

  Background:
    Given I am registered with
      | Email address          | Password         | First name | Last name |
      | john.smith@example.com | StrongPassword1! | John       | Smith     |
    Given I have the following applications assigned to my email 'john.smith@example.com':
      | id          | name                     | description                           | role                | redirectUris               | state      |
      | app-id-1    | My First App             | About my first app                    | DEVELOPER           | https://red1, https://red2 | TESTING    |
      | app-id-2    | My Second App            | About my second app                   | ADMINISTRATOR       | https://red1, https://red2 | TESTING    |
      | app-id-3    | Production App - Active  | An Active Application in Production   | ADMINISTRATOR       | https://red1, https://red2 | PRODUCTION |
      | app-id-4    | Production App - Created | A Created Application in Production   | ADMINISTRATOR       | https://red1, https://red2 | TESTING    |
      | app-id-5    | Production App - Active  | An Active Application in Production   | DEVELOPER           | https://red1, https://red2 | PRODUCTION |
    And applications have the credentials:
      | id          | prodClientId | prodClientSecrets | prodAccessToken | sbxClientId | sbxClientSecrets | sbxAccessToken |
      | app-id-1    | p-clientId   | p-clientSecret    | p-accessToken   | s-clientId  | s-clientSecret   | s-accessToken  |
      | app-id-2    | p-clientId   | p-clientSecret    | p-accessToken   | s-clientId  | s-clientSecret   | s-accessToken  |
      | app-id-3    | p-clientId   | p-clientSecret    | p-accessToken   | s-clientId  | s-clientSecret   | s-accessToken  |
      | app-id-4    | p-clientId   | p-clientSecret    | p-accessToken   | s-clientId  | s-clientSecret   | s-accessToken  |
      | app-id-5    | p-clientId   | p-clientSecret    | p-accessToken   | s-clientId  | s-clientSecret   | s-accessToken  |
    And I am successfully logged in with 'john.smith@example.com' and 'StrongPassword1!'
    Then I am on the 'View all applications' page

  Scenario: TPSD with Admin rights is provided with a link to unsubscribe from an API
    Given the APIs available for 'app-id-3' are:
      | name        | version | status     | requiresTrust   | subscribed | access |
      | api-1       | 0.5     | DEPRECATED | false           | false      | PUBLIC |
      | api-1       | 1.0     | STABLE     | false           | false      | PUBLIC |
      | api-2       | 1.0     | STABLE     | false           | true       | PUBLIC |

    When I navigate to the Subscription page for application with id 'app-id-3'
    And I see correct number of subscriptions for each 'API' api:
      | name  | text            |
      | api-1 | 0 subscriptions |
      | api-2 | 1 subscription  |
    When I click on unsubscribe 'app-id-3' from API 'api-2' version '1.0'
    Then I see on current page:
      | For security reasons we must approve any API subscription changes. This takes up to 2 working days. |
      | Are you sure you want to request to unsubscribe from api-2 1.0? |
    When I click on the radio button with id 'confirm-unsubscribe-yes'
    And I click on submit
    And I am on the unsubcribe request submitted page for application with id 'app-id-3' and api with name 'api-2', context 'api-2' and version '1.0'

  Scenario: TPSD with Admin rights is provided with a link to Cancel the unsubscribe operation
    Given the APIs available for 'app-id-3' are:
      | name        | version | status     | requiresTrust   | subscribed | access |
      | api-1       | 0.5     | BETA       | false           | false      | PUBLIC |
      | api-1       | 1.0     | STABLE     | false           | false      | PUBLIC |
      | api-2       | 1.0     | STABLE     | false           | true       | PUBLIC |

    When I navigate to the Subscription page for application with id 'app-id-3'
    And I see correct number of subscriptions for each 'API' api:
      | name  | text            |
      | api-1 | 0 subscriptions |
      | api-2 | 1 subscription  |
    When I click on unsubscribe 'app-id-3' from API 'api-2' version '1.0'
    Then I see on current page:
      | For security reasons we must approve any API subscription changes. This takes up to 2 working days. |
      | Are you sure you want to request to unsubscribe from api-2 1.0?|
    When I click on the 'Cancel' link
    And I am on the subscriptions page for application with id 'app-id-3'

  Scenario: TPSD subscribes to an API which has no subscription fields
    Given the APIs available for 'app-id-1' are:
      | name        | version | status     | requiresTrust   | subscribed | access |
      | api-1       | 1.0     | BETA |  false           | false      | PUBLIC |
    And there are no subscription fields for 'api-1' version '1.0'
    When I navigate to the Subscription page for application with id 'app-id-1'
    And I successfully subscribe 'app-id-1' to API 'api-1' version '1.0'
    Then I see correct number of subscriptions for each 'API' api:
      | name  | text            |
      | api-1 | 1 subscription  |