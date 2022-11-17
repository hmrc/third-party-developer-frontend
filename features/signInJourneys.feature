@signIn
Feature: Sign in

  Background:
    Given I am registered with
      | Email address          | Password         | First name | Last name |
      | john.smith@example.com | StrongPassword1! | John       | Smith     |
    And I have no application assigned to my email 'john.smith@example.com'

  Scenario: Signing with a valid credentials and no MFA mandated or setup, select email preferences
    Given I navigate to the 'Sign in' page
    And I enter all the fields:
      | email address          | password         |
      | john.smith@example.com | StrongPassword1! |
    When I click on the button with id 'submit'
    Then I am on the 'Recommend Mfa' page
    When I click on the button with id 'skip'
    Then I am on the 'Recommend Mfa Skip Acknowledge' page
    When I click on the button with id 'submit'
    Given I am on the 'No Applications' page
    When I click on the radio button with id 'get-emails'
    And I click on the button with id 'submit'
    Then I am on the 'Email preferences' page

  Scenario: Signing with a valid credentials, no mfa enabled and with a production admin app and the mandated date is in the past
    Given application with name 'My Admin Production App' can be created
    Given I navigate to the 'Sign in' page
    And I enter all the fields:
      | email address          | password         |
      | john.smith@example.com | StrongPassword1! |
    When I click on the button with id 'submit'
    Then I am on the 'Authenticator App Start Page' page
    When I click on the button with id 'submit'
    Then I am on the 'Setup 2SV QR' page
    When I click on the button with id 'submit'
    Then I am on the 'Setup 2SV Enter Access Code' page
    When I enter the correct access code during 2SVSetup with mfaMandated 'true'
    Then I am on the 'Create name for Authenticator App' page
    And  I enter an authenticator app name
    When I click on the button with id 'submit'
    Then I am on the 'Authenticator App Setup Complete' page
    Given 'john.smith@example.com' session is uplifted to LoggedIn
    When I click on the button with id 'link'
    And I am on the 'Sms Mfa Setup Skipped' page
    And I click on the button with id 'submit'
    Then I am on the 'View all applications' page

  Scenario: Signing with a valid credentials and no MFA mandated or setup, start using our rest APIs
    Given I navigate to the 'Sign in' page
    And I enter all the fields:
      | email address          | password         |
      | john.smith@example.com | StrongPassword1! |
    When I click on the button with id 'submit'
    Then I am on the 'Recommend Mfa' page
    When I click on the button with id 'skip'
    Then I am on the 'Recommend Mfa Skip Acknowledge' page
    When I click on the button with id 'submit'
    Given I am on the 'No Applications' page
    When I click on the radio button with id 'use-apis'
    And I click on the button with id 'submit'
    Then I am on the 'Add an application to the sandbox empty nest' page

  Scenario: I have forgotten my password, and I want a link to reset it
    Given I navigate to the 'Sign in' page
    Given I click on the button with id 'forgottenPassword'
    And I enter all the fields:
      | email address          |
      | john.smith@example.com |
    When I click on the button with id 'submit'
    Then I am on the 'Password reset confirmation' page
    Then I should be sent an email with a link to reset for 'john.smith@example.com'

  Scenario: I have a password reset link and I want to reset my password
    Given I click on a valid password reset link for code '1234'
    Then I am on the 'Reset Password' page with code '1234'
    When I enter all the fields:
      | password       | confirmpassword |
      | StrongNewPwd!2 | StrongNewPwd!2  |
    And I click on submit
    Then I am on the 'You have reset your password' page

  Scenario: I have an invalid password reset link and I want to reset my password
    Given I click on an invalid password reset link for code '9876'
    Then I am on the 'Reset password link no longer valid' page