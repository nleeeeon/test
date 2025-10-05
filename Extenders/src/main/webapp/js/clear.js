document.addEventListener('DOMContentLoaded', () => {
	const confettiContainer = document.querySelector('.confetti-container');

	// 紙吹雪を生成する関数
	function createConfetti() {
		const confettiColors = ['#ffcc00', '#ff66b2', '#00ccff', '#ccff33', '#ff0066', '#9933ff'];
		for (let i = 0; i < 50; i++) { // 50個の紙吹雪を生成
			const confetti = document.createElement('div');
			confetti.classList.add('confetti');
			confetti.style.backgroundColor = confettiColors[Math.floor(Math.random() * confettiColors.length)];
			confetti.style.left = Math.random() * 100 + 'vw'; // 画面のどこからでも
			confetti.style.animationDuration = Math.random() * 3 + 2 + 's'; // 2〜5秒
			confetti.style.animationDelay = Math.random() * 2 + 's'; // 0〜2秒遅延
			confetti.style.width = Math.random() * 8 + 5 + 'px'; // 5〜13px
			confetti.style.height = confetti.style.width; // 正方形
			confettiContainer.appendChild(confetti);

			// アニメーション終了後に要素を削除してパフォーマンスを維持
			confetti.addEventListener('animationend', () => {
				confetti.remove();
			});
		}
	}

	// ゲームクリア画面が表示されたら紙吹雪を開始
	setTimeout(createConfetti, 1000); // 1秒後に紙吹雪開始
});