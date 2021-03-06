from org.jboss.seam.contexts import Context, Contexts
from org.jboss.seam.security import Identity
from org.xdi.model.custom.script.type.auth import PersonAuthenticationType
from org.xdi.oxauth.service import UserService
from org.xdi.oxauth.service import UserGroupService
from org.xdi.service import MailService
from org.xdi.util import StringHelper
from org.xdi.util import ArrayHelper

import java
import duo_web
try:
    import json
except ImportError:
    import simplejson as json

class PersonAuthentication(PersonAuthenticationType):
    def __init__(self, currentTimeMillis):
        self.currentTimeMillis = currentTimeMillis

    def init(self, configurationAttributes):
        print "Duo initialization"

        duo_creds_file = configurationAttributes.get("duo_creds_file").getValue2()
        # Load credentials from file
        f = open(duo_creds_file, 'r')
        try:
            creds = json.loads(f.read())
        except:
            print "Failed to load creds from file:", duo_creds_file
            return False
        finally:
            f.close()

        self.ikey = creds["ikey"]
        self.skey = creds["skey"]
        self.akey = creds["akey"]

        self.use_duo_group = False
        if (configurationAttributes.containsKey("duo_group")):
            self.duo_group = configurationAttributes.get("duo_group").getValue2()
            self.use_duo_group = True
            print "Duo initialization. Using Duo only if user belong to group:", self.duo_group

        self.use_audit_group = False
        if (configurationAttributes.containsKey("audit_group")):
            self.audit_group = configurationAttributes.get("audit_group").getValue2()

            if (not configurationAttributes.containsKey("audit_group_email")):
                print "Duo initialization. Property audit_group_email is not specified"
                return False

            self.audit_email = configurationAttributes.get("audit_group_email").getValue2()
            self.use_audit_group = True

            print "Duo initialization. Using audito group:", self.audit_group
            
        if (self.use_duo_group or self.use_audit_group):
            if (not configurationAttributes.containsKey("audit_attribute")):
                print "Duo initialization. Property audit_attribute is not specified"
                return False
            else:
                self.audit_attribute = configurationAttributes.get("audit_attribute").getValue2()


        print "Duo initialized successfully"
        return True   

    def isValidAuthenticationMethod(self, usageType, configurationAttributes):
        return True

    def getAlternativeAuthenticationMethod(self, usageType, configurationAttributes):
        return None

    def authenticate(self, configurationAttributes, requestParameters, step):
        duo_host = configurationAttributes.get("duo_host").getValue2()

        credentials = Identity.instance().getCredentials()
        user_name = credentials.getUsername()

        if (step == 1):
            print "Duo authenticate for step 1"

            user_password = credentials.getPassword()
            logged_in = False
            if (StringHelper.isNotEmptyString(user_name) and StringHelper.isNotEmptyString(user_password)):
                userService = UserService.instance()
                logged_in = userService.authenticate(user_name, user_password)

            if (not logged_in):
                return False

            user = credentials.getUser()
            if (self.use_duo_group):
                print "Duo authenticate for step 1. Checking if user belong to Duo group"
                is_member_duo_group = self.isUserMemberOfGroup(user, self.audit_attribute, self.duo_group)
                if (is_member_duo_group):
                    print "Duo authenticate for step 1. User '" + user.getUserId() + "' member of Duo group"
                    duo_count_login_steps = 2
                else:
                    self.processAuditGroup(user)
                    duo_count_login_steps = 1

                context = Contexts.getEventContext()
                context.set("duo_count_login_steps", duo_count_login_steps)

            return True
        elif (step == 2):
            print "Duo authenticate for step 2"

            passed_step1 = self.isPassedStep1
            if (not passed_step1):
                return False

            sig_response_array = requestParameters.get("sig_response")
            if ArrayHelper.isEmpty(sig_response_array):
                print "Duo authenticate for step 2. sig_response is empty"
                return False

            duo_sig_response = sig_response_array[0]

            print "Duo authenticate for step 2. duo_sig_response: " + duo_sig_response

            authenticated_username = duo_web.verify_response(self.ikey, self.skey, self.akey, duo_sig_response)

            print "Duo authenticate for step 2. authenticated_username: " + authenticated_username + ", expected user_name: " + user_name

            if (not StringHelper.equals(user_name, authenticated_username)):
                return False

            self.processAuditGroup(credentials.getUser())

            return True
        else:
            return False

    def prepareForStep(self, configurationAttributes, requestParameters, step):
        context = Contexts.getEventContext()

        duo_host = configurationAttributes.get("duo_host").getValue2()

        credentials = Identity.instance().getCredentials()
        user_name = credentials.getUsername()

        if (step == 1):
            print "Duo prepare for step 1"

            return True
        elif (step == 2):
            print "Duo prepare for step 2"

            passed_step1 = self.isPassedStep1
            if (not passed_step1):
                return False

            duo_sig_request = duo_web.sign_request(self.ikey, self.skey, self.akey, user_name)
            print "Duo prepare for step 2. duo_sig_request: " + duo_sig_request
            
            context.set("duo_host", duo_host)
            context.set("duo_sig_request", duo_sig_request)

            return True
        else:
            return False

    def getExtraParametersForStep(self, configurationAttributes, step):
        return None

    def getCountAuthenticationSteps(self, configurationAttributes):
        context = Contexts.getEventContext()
        if (context.isSet("duo_count_login_steps")):
            return context.get("duo_count_login_steps")

        return 2

    def getPageForStep(self, configurationAttributes, step):
        if (step == 2):
            return "/auth/duo/duologin.xhtml"
        return ""

    def isPassedStep1():
        credentials = Identity.instance().getCredentials()
        user_name = credentials.getUsername()
        passed_step1 = StringHelper.isNotEmptyString(user_name)

        return passed_step1

    def logout(self, configurationAttributes, requestParameters):
        return True

    def getApiVersion(self):
        return 3

    def isUserMemberOfGroup(self, user, attribute, group):
        is_member = False
        member_of_list = user.getAttributeValues(attribute)
        if (member_of_list != None):
            for member_of in member_of_list:
                if (StringHelper.equalsIgnoreCase(group, member_of)):
                    is_member = True
                    break

        return is_member

    def processAuditGroup(self, user):
        if (self.use_audit_group):
            is_member = self.isUserMemberOfGroup(user, self.audit_attribute, self.audit_group)
            if (is_member):
                print "Duo authenticate for processAuditGroup. User '" + user.getUserId() + "' member of audit group"
                print "Duo authenticate for processAuditGroup. Sending e-mail about user '" + user.getUserId() + "' login to", self.audit_email
                
                # Send e-mail to administrator
                user_id = user.getUserId()
                mailService = MailService.instance()
                subject = "User log in: " + user_id
                body = "User log in: " + user_id
                mailService.sendMail(self.audit_email, subject, body)
