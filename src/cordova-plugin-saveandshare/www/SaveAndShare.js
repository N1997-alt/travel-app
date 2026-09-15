cordova.define("cn.workbuddy.saveandshare.SaveAndShare", function(require, exports, module) {
  var exec = require("cordova/exec");
  module.exports = {
    saveAndShare: function(base64, fileName, mimeType, success, error) {
      exec(success, error, "SaveAndShare", "saveAndShare", [base64, fileName, mimeType]);
    }
  };
});
