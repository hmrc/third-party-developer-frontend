@mfaSetup
Feature: MFA Sms Setup

  Background:
    Given I am registered with
      | Email address          | Password         | First name | Last name | Mfa Setup |
      | john.smith@example.com | StrongPassword1! | John       | Smith     | SMS       |
    And I have no application assigned to my email 'john.smith@example.com'

#   MFA not setup, mfa not mandated, skip mfa setup
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
    Then My device session is not set
    When I click on the radio button with id 'get-emails'
    And I click on the button with id 'submit'
    Then I am on the 'Email preferences' page

#   MFA not setup, mfa not mandated, complete mfa setup
  Scenario: Signing with a valid credentials and no MFA mandated or setup, select email preferences
    Given I navigate to the 'Sign in' page
    And I enter all the fields:
      | email address          | password         |
      | john.smith@example.com | StrongPassword1! |
    When I click on the button with id 'submit'
    Then I am on the 'Recommend Mfa' page
    When I click on the button with id 'submit'
    Then I am on the 'Select MFA' page
    When I click on the radio button with id 'sms-mfa'
    And I click on the button with id 'submit'
    Then I am on the 'Sms mobile number' page
    When  I enter the mobile number then click continue
    Then I am on the 'Sms Access Code' page
#    When I enter the correct Sms access code then click continue
#    Then I am on the 'Sms Setup Complete' page
#    And I click on the button with id 'link'
#    Given 'john.smith@example.com' session is uplifted to LoggedIn
#    And I am on the 'Authenticator App Setup Skipped' page
#    And I click on the button with id 'submit'
#    And I am on the 'No Applications' page
#    Then My device session is not set
#    When I click on the radio button with id 'get-emails'
#    And I click on the button with id 'submit'
#    Then I am on the 'Email preferences' page
    