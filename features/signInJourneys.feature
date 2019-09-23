@signIn
Feature: Sign in

  Background:
    Given I am registered with
      | Email address          | Password         | First name | Last name |
      | john.smith@example.com | StrongPassword1! | John       | Smith     |
    And I have no application assigned to my email 'john.smith@example.com'

  Scenario: Signing with a valid username and password and no MFA mandated or setup
    Given I navigate to the 'Sign in' page
    And I enter all the fields:
      | email address | john.smith@example.com |
      | password | StrongPassword1! |
    When I click on the button with id 'submit'
    Then I am on the 'Recommend Mfa' page
    When I click on the button with id 'skip'
    Then I am on the 'Recommend Mfa Skip Acknowledge' page
    When I click on the button with id 'submit'
    Then I am on the 'Manage applications empty nest' page