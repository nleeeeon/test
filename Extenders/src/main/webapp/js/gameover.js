document.addEventListener('DOMContentLoaded', () => {
	const finalScoreElement = document.getElementById('final-score');

	// URLパラメータからスコアを取得 (例: gameover.html?score=5400)
	let score = 0;
	const urlParams = new URLSearchParams(window.location.search);
	if (urlParams.has('score')) {
		score = parseInt(urlParams.get('score'), 10) || 0;
	}

	// スコアをカウントアップ表示する
	let currentScore = 0;
	const scoreAnimation = setInterval(() => {
		// 徐々に目標スコアに近づける
		const increment = Math.ceil((score - currentScore) / 15);
		currentScore += increment;

		if (currentScore >= score) {
			currentScore = score;
			clearInterval(scoreAnimation);
		}
		// 5桁のゼロ埋めで表示
		finalScoreElement.textContent = String(currentScore).padStart(5, '0');
	}, 50);
});