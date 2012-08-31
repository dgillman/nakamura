Feature: Members of the administrators group have the same privileges as admin

Background:
  Given "alice" is a member of the administrators group

Scenario: A member of the administrators groups can view private documents in another user's profile
  Given "bob" creates a private document
  Then Verify that "alice" can view the private document
  And Verify that "carol" cannot view the private document

Scenario: A member of the administrators group can create an activity on any node
  Given "bob" creates a private document
  Then Verify that "alice" can post an activity on the private document
  And Verify that "carol" cannot post an activity on the private document

Scenario: A member of the administrators group retrieves restricted results in searches
  Given "bob" creates a private document named "mydocument" with tag "foo"
  Then Verify "alice" retrieves "mydocument" when searching on the tag "foo"
  And Verify "carol" does not retrieve "mydocument" when searching on the tag "foo"

Scenario: A member of the administrators group can initiate a system upgrade
  Then Verify "alice" can initiate the upgrade process
  And Verify "bob" cannot initiate the upgrade process

Scenario: A member of the administrators group can manage group membership
  Given A group named "testgroup" exists
  Then Verify "bob" cannot add member "carol" to Group "testgroup"
  And Verify "alice" can add member "carol" to Group "testgroup"

Scenario: A member of the administrators group can create a user
  Then Verify "alice" can create a user