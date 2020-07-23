@signIn
Feature: Sign in

  Background:
    Given I am registered with
      | Email address          | Password         | First name | Last name |
      | john.smith@example.com | StrongPassword1! | John       | Smith     |
    And I have no application assigned to my email 'john.smith@example.com'

  Scenario: Signing with a valid credentials and no MFA mandated or setup
    Given I navigate to the 'Sign in' page
    And I enter all the fields:
      | email address          | password         |
      | john.smith@example.com | StrongPassword1! |
    When I click on the button with id 'submit'
    Then I am on the 'Recommend Mfa' page
    When I click on the button with id 'skip'
    Then I am on the 'Recommend Mfa Skip Acknowledge' page
    When I click on the button with id 'submit'
    Then I am on the 'Add an application to the sandbox empty nest' page


  Scenario: Signing with a valid credentials, no mfa enabled and with a production admin app and the mandate date is in the past
    Given application with name 'My Admin Production App' can be created
    Given I navigate to the 'Sign in' page
    And I enter all the fields:
      | email address          | password         |
      | john.smith@example.com | StrongPassword1! |
    When I click on the button with id 'submit'
    Then I am on the 'Protect Account' page
    When I click on the button with id 'submit'
    Then I am on the 'Setup 2SV QR' page
    When I click on the button with id 'submit'
    Then I am on the 'Setup 2SV Enter Access Code' page
    When I enter the correct access code and continue
    Then I am on the 'Protect Account Complete' page
    Given 'john.smith@example.com' session is uplifted to LoggedIn
    When I click on the button with id 'submit'
    Then I am on the 'View all applications' page


  Scenario: I have forgotten my password, and I want a link to reset it
    Given I navigate to the 'Sign in' page
    Given I click on the button with id 'forgottenPassword'
    And I enter all the fields:
      | email address          |
      | john.smith@example.com |
    When I click on the button with id 'submit'
    Then I am on the 'Password reset confirmation' page
    Then I should be sent an email with a link to reset for 'john.smith@example.com'