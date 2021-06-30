(function () {
  function showSurvey() {
    var feedbackSurvey = document.getElementById("feedback");
    var feedbackButton = document.getElementById("survey");
    feedbackSurvey.style.display = "none";
    feedbackButton.style.display = "block";
  }
  function closeSurvey() {
    var feedbackSurvey = document.getElementById("feedback");
    var feedbackButton = document.getElementById("survey");
    feedbackSurvey.style.display = "block";
    feedbackButton.style.display = "none";
  }
  document.querySelector("#show-survey").addEventListener("click", showSurvey);
  document.querySelector("#close-survey").addEventListener("click", closeSurvey);
})();