@create_user_config_path = "/system/console/configMgr/org.sakaiproject.nakamura.user.lite.servlet.LiteCreateSakaiUserServlet"

Given /^A group named "([^"]*)" exists$/ do |groupName|
  group = @um.create_group (groupName + "-" + @m)
end

When /^I add member "([^"]*)" to Group "([^"]*)"$/ do |username, groupName|
  @s.execute_post(@s.url_for("/system/userManager/group/#{groupName}-#{@m}.update.html"), {":member" => "#{username}-#{@m}"})
end

Then /^Verify "([^"]*)" can add member "([^"]*)" to Group "([^"]*)"$/ do |adminuser, username, groupName|
  olduser = @s.get_user()
  user = User.new(adminuser + @m)
  @s.switch_user(user)

  res = @s.execute_post(@s.url_for("/system/userManager/group/#{groupName}-#{@m}.update.html"), {":member" => "#{username}-#{@m}"})

  if (res.code.to_i != 200)
    @log.error ("response code: " + res.code.to_s)
    @log.error (res.body)
    @log.error ("#{adminuser} could not add #{username} to #{groupName}")
  end
  @s.switch_user(olduser)
end

Then /^Verify "([^"]*)" cannot add member "([^"]*)" to Group "([^"]*)"$/ do |adminuser, username, groupName|
  olduser = @s.get_user()
  user = User.new(adminuser + @m)
  @s.switch_user(user)

  res = @s.execute_post(@s.url_for("/system/userManager/group/#{groupName}-#{@m}.update.html"), {":member" => "#{username}-#{@m}"})

  if (res.code.to_i != 403)
    @log.error ("response code: " + res.code.to_s)
    @log.error (res.body)
    @log.error ("#{adminuser} should not be able to add #{username} to #{groupName}")
  end
  @s.switch_user(olduser)
end

Given /^the Group "([^"]*)" is a Collection$/ do |groupName|
  @s.switch_user(User.admin_user())
  @s.execute_post(@s.url_for("/system/userManager/group/#{groupName}-#{@m}.update.html"), {"sakai:pseudoGroup" => true, "sakai:category" => "collection"})
end

Given /^User self\-registration is disabled$/ do
  params = {
          "action" => "ajaxConfigManager",
          "apply" => true,
          "self.registration.enabled" => false,
          "propertylist" => "self.registration.enabled"
      }
      @s.switch_user(User.admin_user)
      @s.execute_post(@s.url_for("/system/console/configMgr/org.sakaiproject.nakamura.user.lite.servlet.LiteCreateSakaiUserServlet"), params)
end

Given /^User self\-registration is enabled$/ do
  params = {
          "action" => "ajaxConfigManager",
          "apply" => true,
          "self.registration.enabled" => true,
          "propertylist" => "self.registration.enabled"
      }
      @s.switch_user(User.admin_user)
      @s.execute_post(@s.url_for("/system/console/configMgr/org.sakaiproject.nakamura.user.lite.servlet.LiteCreateSakaiUserServlet"), params)
end

When /^I try to create a new user$/ do
  @user = User.anonymous()
  @s.switch_user(@user)
  @user = @um.create_user("foo-#{@m}")
end

When /^I try to create a new user with captcha$/ do
  @user = User.anonymous()
  @s.switch_user(@user)
  @httpResponse = @s.execute_post(@s.url_for("/system/userManager/user.create.html"), {":create-auth" => "reCAPTCHA.net"})
  puts @httpResponse.code
end

Given /^the Group "([^"]*)" is the managers group of the Group "([^"]*)"$/ do |managersGroupName, groupName|
  @s.switch_user(User.admin_user())
  @s.execute_post(@s.url_for("/system/userManager/group/#{groupName}-#{@m}.update.html"), {"sakai:managers-group" => "#{managersGroupName}-#{@m}"})
end

When /^I try to create a new user with no name$/ do
  @httpResponse = @s.execute_post(@s.url_for("/system/userManager/user.create.html"), {"pwd" => "shhh", "pwdConfirm" => "shhh"})
end

When /^I try to create a new user without password$/ do
  @httpResponse = @s.execute_post(@s.url_for("/system/userManager/user.create.html"), {":name" => "lucy-#{@m}", "pwdConfirm" => "shhh"})
end

When /^I try to create a new user without matching password confirmation$/ do
  @httpResponse = @s.execute_post(@s.url_for("/system/userManager/user.create.html"), {":name" => "lucy-#{@m}", "pwd" => "secret", "pwdConfirm" => "shhh"})
end
