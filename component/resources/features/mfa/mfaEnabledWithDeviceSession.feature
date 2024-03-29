@mfaEnabledWithDeviceSession
Feature: MFA Enabled Journey  MFA Enabled Journey User with Existing Device Session

  Background:
    Given I am mfaEnabled and with a DeviceSession registered with
      | Email address          | Password         | First name | Last name |
      | john.smith@example.com | StrongPassword1! | John       | Smith     |
    And I have no application assigned to my email 'john.smith@example.com'

#   MFA already setup, device session exists, mfa not mandated
  Scenario: Signing with a valid credentials and no MFA mandated or setup, select email preferences
    Given I navigate to the 'Sign in' page
    And I enter all the fields:
      | email address          | password         |
      | john.smith@example.com | StrongPassword1! |
    And I already have a device cookie
    When I click on the button with id 'submit'
    Given I am on the 'No Applications' page
    Then My device session is set
    When I click on the radio button with id 'get-emails'
    And I click on the button with id 'submit'
    Then I am on the 'Email preferences' page
