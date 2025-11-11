Feature:
  Developer Logout

  Background:
    Given I am registered with
      | Email address          | Password         | First name | Last name | Mfa Setup |
      | john.smith@example.com | StrongPassword1! | John       | Smith     |           |
    And I have no application assigned to my email 'john.smith@example.com'

    @APIS-1467
    Scenario: TPDF should respond properly if logout fails
      Given I navigate to the 'Sign in' page
      And I successfully log in with 'john.smith@example.com' and 'StrongPassword1!' skipping 2SV
      And I am on the 'No Applications' page
      When I attempt to Sign out when the session expires
      Then I am not logged in


