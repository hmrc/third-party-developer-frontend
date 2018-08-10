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
    Then I am on the 'Add application success' page
    And I see:
      | You have added Parsley App.                                                                                |
      | You have admin rights over the application.                                                            |
      | What happens next?                                                                                         |
      | To get your production credentials, you must submit your application for checking. |
      | This takes up to 2 working days.                   |
    And there is a link to submit your application for checking 'applicationId', with the text 'Start'