require 'json'

@private_poolId

Given /^"([^"]*)" is a member of the administrators group$/ do |user|
  olduser = @s.get_user()
  @s.switch_user(SlingUsers::User.admin_user())
  @s.execute_post(@s.url_for("/system/userManager/group/administrators.update.html"), {":member" => "#{user + @m}"})
  @s.switch_user(olduser)
end

When /^"([^"]*)" creates a private document$/ do |userName|
  olduser = @s.get_user()

  user = User.new (userName + @m)
  @s.switch_user(user)

  randPart = rand(10000000).to_s()
  id = "id" + randPart
  title = "document_" + randPart

  structure0 = "{\"page1\":{\"_ref\":\"#{id}\",\"_order\":0,\"_title\":\"#{title}\",\"main\":{\"_ref\":\"#{id}\",\"_order\":0,\"_title\":\"#{title}\"}}}"

  res = @s.execute_post(@s.url_for("/system/pool/createfile"),
    {"structure0" => structure0,
     "mimeType" => "x-sakai/document",
     "sakai:schemaversion" => 2,
     "_charset_" => "utf-8"
    })

  if ( res.code.to_i != 201 )
    @log.error("response code: " + res.code.to_s)
    @log.error(res.body)
    @log.error(" Unable to create file #{title}")
    return false
  end

  data = JSON.parse(res.body)

  @private_poolId = data['_contentItem']['poolId']

  content = "{\"#{id}\":{\"rows\":{\"__array__0__\":{\"id\":\"id14318149\",\"columns\":{\"__array__0__\":{\"width\":1,\"elements\":\"\"}}}}}}"

  res = @s.execute_post(@s.url_for("/p/#{@private_poolId}"),
     {
       ":operation"   =>  "import",
       ":contentType" =>  "json",
       ":merge" =>  "true",
       ":replace" =>  "true",
       ":replaceProperties" =>  "true",
       "_charset_" =>  "utf-8",
       ":content" =>  content
     })

  if ( res.code.to_i != 201)
    @log.error("response code: " + res.code.to_s)
    @log.error(res.body)
    @log.error(" Unable to create file #{title}")
    return false
  end

  res = @s.execute_post(@s.url_for("/p/#{@private_poolId}/#{id}.save.json"),
     {
       "sling:resourceType"   =>    "sakai/pagecontent",
       "sakai:pagecontent"    =>    "{\"rows\":[{\"id\":\"id14318149\",\"columns\":[{\"width\":1,\"elements\":[]}]}]}",
       "_charset_"            =>    "utf-8"
     })

  res = @s.execute_post(@s.url_for("/system/batch"),
     {
       "requests"             =>    "[{\"url\":\"/p/#{@private_poolId}.members.html\",\"method\":\"POST\",\"parameters\":{\":viewer@Delete\":[\"anonymous\",\"everyone\"]}},{\"url\":\"/p/#{@private_poolId}.modifyAce.html\",\"method\":\"POST\",\"parameters\":{\"principalId\":[\"everyone\"],\"privilege@jcr:read\":\"denied\"}},{\"url\":\"/p/#{@private_poolId}.modifyAce.html\",\"method\":\"POST\",\"parameters\":{\"principalId\":[\"anonymous\"],\"privilege@jcr:read\":\"denied\"}}]",
       "_charset_"            =>    "utf-8"
     })

  res = @s.execute_post(@s.url_for("/p/#{@private_poolId}/#{id}.save.json"),
     {
       "sakai:pooled-content-file-name"   =>  "#{title}",
       "sakai:description"                =>  "",
       "sakai:permissions"                =>  "private",
       "sakai:copyright"                  =>  "creativecommons",
       "sakai:allowcomments"              =>  "true",
       "sakai:showcomments"               =>  "true",
       "_charset_"                        =>  "utf-8"
     })

  @s.switch_user(olduser)
end

Then /^Verify that "([^"]*)" can view the private document$/ do |userName|
  olduser = @s.get_user()

  user = User.new(userName + @m)

  @s.switch_user(user)

  res = @s.execute_get(@s.url_for("/p/#{@private_poolId}"), {})

  if (res.code.to_i != 200)
    @log.error("response code: " + res.code.to_s)
    @log.error(res.body)
    @log.error("Unable to view document with ID #{@private_poolId}")
    return false
  end

  @s.switch_user(olduser)
end

Then /^Verify that "([^"]*)" cannot view the private document$/ do |userName|
  olduser = @s.get_user()

  user = User.new(userName + @m)

  @s.switch_user(user)

  res = @s.execute_get(@s.url_for("/p/#{@private_poolId}"), {})

  if (res.code.to_i != 404)
    @log.error("response code: " + res.code.to_s)
    @log.error(res.body)
    @log.error("Able to view document with ID #{@private_poolId}")
    return false
  end

  @s.switch_user(olduser)
end

Then /^Verify "([^"]*)" can create a user$/ do |userName|
  olduser = @s.get_user()

  user = User.new(userName + @m)

  @s.switch_user(user)

  res = @s.execute_post(@s.url_for("/system/userManager/user"),
     {
       ":name"                            =>  "newuser" + @m,
       "pwd"                              =>  "bogus pw",
       "pwdConfirm"                       =>  "bogus pw",
       "additionalParam"                  =>  "additional value",
       "_charset_"                        =>  "utf-8"
     })

  if (res.code.to_i != 200)
    @log.error("response code: " + res.code.to_s)
    @log.error(res.body)
    @log.error("Could not create user")
    return false
  end

  @s.switch_user(olduser)
end

Then /^Verify "([^"]*)" can initiate the upgrade process$/ do |userName|
  olduser = @s.get_user()

  user = User.new(userName + @m)

  @s.switch_user(user)

  res = @s.execute_post(@s.url_for("/system/sparseupgrade"),
     {
       "dryRun"                           =>  true,
       "limit"                            =>  100
     })

  if (res.code.to_i != 200)
    @log.error("response code: " + res.code.to_s)
    @log.error(res.body)
    @log.error("Could not initiate upgrade process")
    return false
  end

  @s.switch_user(olduser)
end

Then /^Verify "([^"]*)" cannot initiate the upgrade process$/ do |userName|
  olduser = @s.get_user()

  user = User.new(userName + @m)

  @s.switch_user(user)

  res = @s.execute_post(@s.url_for("/system/sparseupgrade"),
     {
       "dryRun"                           =>  true,
       "limit"                            =>  100
     })

  if (res.code.to_i != 403)
    @log.error("response code: " + res.code.to_s)
    @log.error(res.body)
    @log.error("Should not be able to initiate upgrade process")
    return false
  end

  @s.switch_user(olduser)
end

Given /^"([^"]*)" creates a private document named "([^"]*)" with tag "([^"]*)"$/ do |userName, title, tag|
  olduser = @s.get_user()

  user = User.new (userName + @m)
  @s.switch_user(user)

  randPart = rand(10000000).to_s()
  id = "id" + randPart
  title += @m
  @docTitle = title

  uploadResult = @fm.upload_pooled_file(title, "bogus data", "text/plain")

  jsonResult = JSON.parse(uploadResult.body)
  poolId = jsonResult[title]['poolId']

  res = @s.execute_post(@s.url_for("/p/#{poolId}"),
     {
       ":operation"           =>    "tag",
       "key"                  =>    "/tags/#{tag + @m}",
       "_charset_"            =>    "utf-8"
     })

  res = @s.execute_post(@s.url_for("/system/batch"),
     {
       "requests"             =>    "[{\"url\":\"/p/#{poolId}.members.html\",\"method\":\"POST\",\"parameters\":{\":viewer@Delete\":[\"anonymous\",\"everyone\"]}},{\"url\":\"/p/#{poolId}.modifyAce.html\",\"method\":\"POST\",\"parameters\":{\"principalId\":[\"everyone\"],\"privilege@jcr:read\":\"denied\"}},{\"url\":\"/p/#{poolId}.modifyAce.html\",\"method\":\"POST\",\"parameters\":{\"principalId\":[\"anonymous\"],\"privilege@jcr:read\":\"denied\"}}]",
       "_charset_"            =>    "utf-8"
     })

  res = @s.execute_post(@s.url_for("/p/#{poolId}/#{id}.save.json"),
     {
       "sakai:pooled-content-file-name"   =>  "#{title}",
       "sakai:description"                =>  "",
       "sakai:permissions"                =>  "private",
       "sakai:copyright"                  =>  "creativecommons",
       "sakai:allowcomments"              =>  "true",
       "sakai:showcomments"               =>  "true",
       "_charset_"                        =>  "utf-8"
     })

  @s.switch_user(olduser)

  #wait for indexing
  sleep(1)
end

Then /^Verify "([^"]*)" retrieves "([^"]*)" when searching on the tag "([^"]*)"$/ do |adminuser, doc, tag|
  olduser = @s.get_user()

  user = User.new (adminuser + @m)
  @s.switch_user(user)

  res = @s.execute_get(@s.url_for("/var/search/general.json?q=#{tag + @m}&tags=&sortOn=score&sortOrder=desc&page=0&items=18&_charset_=utf-8"))

  if ( res.code.to_i != 200)
    @log.error("response code: " + res.code.to_s)
    @log.error(res.body)
    @log.error("search failed")
    return false
  end

  data = JSON.parse(res.body)
  found = false

  data['results'].each {
    |result|
    if (result['sakai:pooled-content-file-name'].eql? @docTitle)
      found = true;
      break;
    end
  }

  if (!found)
    @log.error(res.body)
    @log.error("document \"#{@docTitle}\" not searchable by user \"#{adminuser}\"")
    return false
  end

  @s.switch_user(olduser)

end

Then /^Verify "([^"]*)" does not retrieve "([^"]*)" when searching on the tag "([^"]*)"$/ do |adminuser, doc, tag|
  olduser = @s.get_user()

  user = User.new (adminuser + @m)
  @s.switch_user(user)

  res = @s.execute_get(@s.url_for("/var/search/general.json?q=#{tag + @m}&tags=&sortOn=score&sortOrder=desc&page=0&items=18&_charset_=utf-8"))

  if ( res.code.to_i != 200)
    @log.error("response code: " + res.code.to_s)
    @log.error(res.body)
    @log.error("search failed")
    return false
  end

  data = JSON.parse(res.body)
  found = false

  data['results'].each {
    |result|
    if (result['sakai:pooled-content-file-name'].eql? @docTitle)
      found = true;
      break;
    end
  }

  if (found)
    @log.error(res.body)
    @log.error("document \"#{@docTitle}\" should not be searchable by user \"#{adminuser}\"")
    return false
  end

  @s.switch_user(olduser)

end

Then /^Verify that "([^"]*)" can post an activity on the private document$/ do |userName|
  olduser = @s.get_user()

  user = User.new (userName + @m)
  @s.switch_user(user)

  res = @s.execute_post(@s.url_for("/p/#{@private_poolId}.activity.json"),
     {
       "sakai:activity-appid"           =>  "content",
       "sakai:activity-templateid"      =>  "default",
       "sakai:activityMessage"          =>  "UPDATED_COPYRIGHT"
     })

  if (res.code.to_i != 200)
    @log.error ("response code: " + res.code.to_s)
    @log.error (res.body)
    @log.error ("user could not create activity")
  end
  @s.switch_user(olduser)

end

Then /^Verify that "([^"]*)" cannot post an activity on the private document$/ do |userName|
  olduser = @s.get_user()

  user = User.new (userName + @m)
  @s.switch_user(user)

  res = @s.execute_post(@s.url_for("/p/#{@private_poolId}.activity.json"),
     {
       "sakai:activity-appid"           =>  "content",
       "sakai:activity-templateid"      =>  "default",
       "sakai:activityMessage"          =>  "UPDATED_COPYRIGHT"
     })

  if (res.code.to_i == 200)
    @log.error ("response code: " + res.code.to_s)
    @log.error (res.body)
    @log.error ("user was able to create activity")
  end
  @s.switch_user(olduser)

end