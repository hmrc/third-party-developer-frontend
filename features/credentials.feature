@credentials
Feature: Application Credentials

  Background:
    Given I am registered with
      | Email address          | Password         | First name | Last name |
      | john.smith@example.com | StrongPassword1! | John       | Smith     |
    And I have the following applications assigned to my email 'john.smith@example.com':
      | id       | name          | description         | role          | state      | environment |
      | app-id-1 | My First App  | About my first app  | ADMINISTRATOR | PRODUCTION | PRODUCTION  |
      | app-id-2 | My Second App | About my second app | DEVELOPER     | PRODUCTION | SANDBOX     |
      | app-id-3 | My Third App  | About my third app  | DEVELOPER     | PRODUCTION | PRODUCTION  |
    And applications have the credentials:
      | id       | prodClientId | prodClientSecrets | prodAccessToken | sbxClientId | sbxClientSecrets | sbxAccessToken |
      | app-id-1 | p-clientId   | p-clientSecret    | p-accessToken   | s-clientId  | s-clientSecret   | s-accessToken  |
      | app-id-2 | p-clientId   | p-clientSecret    | p-accessToken   | s-clientId  | s-clientSecret   | s-accessToken  |
      | app-id-3 | p-clientId   | p-clientSecret    | p-accessToken   | s-clientId  | s-clientSecret   | s-accessToken  |
    And I am successfully logged in with 'john.smith@example.com' and 'StrongPassword1!'
    Then I am on the 'View all applications' page


  @APIS-1065
  Scenario: TPSD views application production credentials
    When I navigate to the Credentials page for application with id 'app-id-1'
    Then I see:
      | Client ID     |
      | Client secret |
      | Server token  |
    And I see data in fields:
      | clientid    | p-clientId    |
      | accesstoken | p-accessToken |
    When I click on the 'clientsecret-toggle' data link
    And I enter all the fields:
      | password | StrongPassword1! |
    And I click on the 'Submit' button
    #wait to ajax call to return response
    Then Pause for 1 seconds
    Then I see data in fields:
      | clientsecret | p-clientSecret |
