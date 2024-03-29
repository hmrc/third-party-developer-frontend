@smsMfaEnabledWithoutDeviceSession
Feature: Sms Enabled as MFA Method Journey User with No Device Session

  Background:
    Given I have SMS enabled as MFA method, without a DeviceSession and registered with
      | Email address          | Password         | First name | Last name |
      | john.smith@example.com | StrongPassword1! | John       | Smith     |
    And I have no application assigned to my email 'john.smith@example.com'

  Scenario: Signing with a valid credentials and no MFA mandated but is setup, select email preferences
    Given I navigate to the 'Sign in' page
    And I enter all the fields:
      | email address          | password         |
      | john.smith@example.com | StrongPassword1! |
    When I click on the button with id 'submit'
    Given I am on the 'Sms Login Access Code' page
    Then My device session is not set
    When I enter the correct access code for SMS and click remember me for 7 days then click continue
    Then My device session is set
    And I am on the 'Authenticator App Mfa Setup Reminder' page
    And I click on the button with id 'link'
    And I am on the 'Authenticator App Setup Skipped' page
    And I click on the button with id 'submit'
    Given I am on the 'No Applications' page
    When I click on the radio button with id 'get-emails'
    And I click on the button with id 'submit'
    Then I am on the 'Email preferences' page

  Scenario: Signing with a valid credentials and no MFA mandated but is setup, select email preferences
    Given I navigate to the 'Sign in' page
    And I enter all the fields:
      | email address          | password         |
      | john.smith@example.com | StrongPassword1! |
    When I click on the button with id 'submit'
    Given I am on the 'Sms Login Access Code' page
    Then My device session is not set
    When I enter the correct access code SMS and do NOT click remember me for 7 days then click continue
    And I am on the 'Authenticator App Mfa Setup Reminder' page
    And I click on the button with id 'link'
    And I am on the 'Authenticator App Setup Skipped' page
    And I click on the button with id 'submit'
    Given I am on the 'No Applications' page
    Then My device session is not set
    When I click on the radio button with id 'get-emails'
    And I click on the button with id 'submit'
    Then I am on the 'Email preferences' page