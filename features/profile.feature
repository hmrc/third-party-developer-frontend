Feature: Developer views/updates profile

  Background:
    Given I am registered with
      | Email address          | Password         | First name | Last name |
      | john.smith@example.com | StrongPassword1! | John       | Smith     |
    And I have no application assigned to my email 'john.smith@example.com'
    And I am successfully logged in with 'john.smith@example.com' and 'StrongPassword1!'
    Then I am on the 'Using the Developer Hub' page
    When I click on the 'John Smith' link
    Then I am on the 'Manage profile' page
    And I see text in fields:
      | name | John Smith |

  @APIS-568
  Scenario: TPSD edits profile
    Given I want to successfully change my profile
    When I click on the button with id 'change'
    Then I am on the 'Change profile details' page
    When I enter all the fields:
      | firstname       | lastname |
      | Joe             | Bloggs   |
    And I click on submit
    Then I am on the 'Manage profile' page
    And the user-nav header contains a 'Joe Bloggs' link
    And The current page contains link 'Continue to your profile' to 'Manage profile'

  @API-141
  Scenario: TPSD edits password
    Given I want to successfully change my password
    When I click on the 'Change password' link
    When I enter all the fields:
      | currentpassword  | password       | confirmpassword |
      | StrongPassword1! | StrongNewPwd!2 | StrongNewPwd!2  |
    And I click on submit
    Then I am on the 'Edit password success' page
