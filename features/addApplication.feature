@addApplication @API-430
Feature: Add Application

  Background:
    Given I am registered with
      | Email address          | Password         | First name | Last name |
      | john.smith@example.com | StrongPassword1! | John       | Smith     |
    And I have no application assigned to my email 'john.smith@example.com'
    And I am successfully logged in with 'john.smith@example.com' and 'StrongPassword1!'

  Scenario: TPSD successfully adds their application on the API developer hub
    Given I navigate to the 'Add application' page
    And application with name 'Parsley App' can be created
    And I enter all the fields:
      | applicationName | Parsley App        |
      | description     | Valid description. |
    And I click on the radio button with id 'production'
    When I click on the 'Add' button
    And I am on the 'Add application success' page