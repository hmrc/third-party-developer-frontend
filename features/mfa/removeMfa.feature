@removeMfa
Feature: Remove MFA. User with MFA enabled and Existing Device Session

  Background:
    Given I am mfaEnabled and with a DeviceSession registered with
      | Email address          | Password         | First name | Last name |
      | john.smith@example.com | StrongPassword1! | John       | Smith     |
    And I have no application assigned to my email 'john.smith@example.com'

#   MFA already setup, device session exists, mfa not mandated
  Scenario: Signing with a valid credentials and no MFA mandated or setup, remove MFA
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
    Given I navigate to the 'Account protection' page
    Then I am on the 'Account protection' page

    #TODO: This need fixing when remove mfa feature is available
#    And I click on the button with id 'submit'
#    Then I am on the 'Confirm 2SV removal' page
#    When I click on the radio button with id 'confirm-remove-2sv-yes'
#    And I click on the button with id 'submit'
#    Then I am on the '2SV remove' page
#    When I enter the correct access code during remove2SV then click continue
#    Then I am on the '2SV removal complete' page