Feature: Developer requests their account to be deleted

  Background:
    Given I am registered with
      | Email address          | Password         | First name | Last name | Mfa Setup |
      | john.smith@example.com | StrongPassword1! | John       | Smith     |           |
    And I have no application assigned to my email 'john.smith@example.com'
    And I successfully log in with 'john.smith@example.com' and 'StrongPassword1!' skipping 2SV
    When I click on the 'John Smith' link
    Then I am on the 'Manage profile' page
    And I see text in fields:
      | name | John Smith  |
    And I see a link to request account deletion

  @APIS-3430
  Scenario: TPSD sees account deletion link and clicks it
    When I see a link to request account deletion
    And I click on the request account deletion link
    Then I am on the 'Account deletion confirmation' page
    When I select the confirmation option with id 'deleteAccountYes'
    And I click on the account deletion confirmation submit button
    Then I am on the 'Account deletion request submitted' page
    Then a deskpro ticket is generated with subject 'Delete Developer Account Request'
