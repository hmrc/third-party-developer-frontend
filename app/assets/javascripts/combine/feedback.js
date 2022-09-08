(function () {
  function showSurvey() {
    var feedbackSurvey = document.getElementById("feedback");
    var feedbackDiv = document.getElementById("survey");
    var feedbackButton = document.getElementById("show-survey");
    feedbackSurvey.style.display = "none";
    feedbackDiv.style.display = "block";
    feedbackButton.setAttribute("aria-expanded", true);
  }
  function closeSurvey() {
    var feedbackSurvey = document.getElementById("feedback");
    var feedbackDiv = document.getElementById("survey");
    var feedbackButton = document.getElementById("show-survey");
    feedbackSurvey.style.display = "block";
    feedbackDiv.style.display = "none";
    feedbackButton.setAttribute("aria-expanded", false);
  }
  document.querySelector("#show-survey").addEventListener("click", showSurvey);
  document.querySelector("#close-survey").addEventListener("click", closeSurvey);
})();