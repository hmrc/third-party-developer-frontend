Feature:
  Developer Logout

  Background:
    Given I am registered with
      | Email address          | Password         | First name | Last name |
      | john.smith@example.com | StrongPassword1! | John       | Smith     |
    And I have no application assigned to my email 'john.smith@example.com'

    @APIS-1467
    Scenario: TPDF should respond properly if logout fails
      Given I navigate to the 'Sign in' page
      And I fill in the login form with
        | email address          | password         |
        | john.smith@example.com | StrongPassword1! |
      When I click on submit
      Then I am logged in as 'John Smith'
      And I am on the 'Manage applications empty nest' page
      When I attempt to Sign out when the session expires
      Then I am on the 'Logout survey' page
      When I click on the 'No thanks, sign me out' link
      Then I see on current page:
        | You are now signed out |
      And I am not logged in


