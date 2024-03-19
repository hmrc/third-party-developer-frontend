Feature: Resend verification

@APIS-1745
Scenario: Resend verification email successfully in the Developer Hub
    Given I navigate to the 'Registration' page
    And I see:
      | First name                                                                                                                              |
      | Last name                                                                                                                               |
      | Email address                                                                                                                           |
      | Create password                                                                                                                         |
      | Your password must be at least 12 characters and contain at least one number, lowercase letter, uppercase letter and special character  |
      | Confirm password                                                                                                                        |
    And I enter valid information for all fields:
      | first name | last name | email address          | password     | confirm password |
      | John       | Smith     | john.smith@example.com | A1@wwwwwwwww | A1@wwwwwwwww     |

    Then I click on submit
    Then I expect a resend call from 'john.smith@example.com'
    Then I am on the 'Email confirmation' page
    And I see:
      | We have sent a confirmation email to john.smith@example.com. |
      | Click on the link in the email to verify your account.       |
      | I have not received the email                                |
    When I click on the 'I have not received the email' link
    Then I am on the 'Resend confirmation' page
    And I see:
      | Emails can take a few minutes to arrive. If you have not received it check your spam folder, or we can resend it. |
    Then I click on the 'Resend' link
    Then I am on the 'Email confirmation' page
        And I see:
          | We have sent a confirmation email to john.smith@example.com. |
          | Click on the link in the email to verify your account.       |
          | I have not received the email                                |

