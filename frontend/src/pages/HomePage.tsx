import { useLanguage } from '../i18n/LanguageContext'
import { useState } from 'react'
import { Link } from 'react-router'

export default function HomePage() {
  const { t } = useLanguage()
  const [flipped, setFlipped] = useState(false)

  return (
    <div className="home-page">
      <section className="hero" aria-labelledby="home-title">
        <div className="hero-copy">
          <span className="hero-badge"><span aria-hidden="true">✦</span> {t("SMALL STEPS. BIG PROGRESS.")}</span>
          <h1 id="home-title">{t("Master your")}<br /><span>{t("learning.")}</span></h1>
          <p>{t("Your ideas, your flashcards, your pace. Turn what you want to know into something you remember.")}</p>
          <div className="hero-actions">
            <Link className="button button-primary" to="/register">{t("Start learning")} <span aria-hidden="true">→</span></Link>
            <Link className="button button-outline" to="/login">{t("I already have an account")}</Link>
          </div>
          <p className="hero-footnote">{t("A little practice today. A little more confidence tomorrow.")}</p>
        </div>
        <div className="hero-preview" id="preview">
          <div className="preview-topline"><span className="preview-dot" /> {t("A MOMENT OF DISCOVERY")}</div>
          <div className="flashcard-stack">
            <button className={`demo-card${flipped ? ' is-flipped' : ''}`} type="button"
              onClick={() => setFlipped((value) => !value)} aria-pressed={flipped}
              aria-label={flipped ? t("Hide the sample answer") : t("Reveal the sample answer")}>
              <span className="card-category">{t("ENGLISH")} <span aria-hidden="true">→</span> {t("POLISH")}</span>
              <span className="card-side">{flipped ? t("THE ANSWER") : t("DO YOU KNOW THIS WORD?")}</span>
              <span className="card-word">{flipped ? 'ciekawość' : 'curiosity'}</span>
              <span className="card-pronunciation">{flipped ? 'A desire to learn something new.' : '/ˌkjʊəriˈɒsəti/'}</span>
              <span className="card-flip-hint"><span aria-hidden="true">↻</span> {flipped ? t("Click to see the question") : t("Click to reveal the answer")}</span>
            </button>
          </div>
          <p className="preview-caption">{t("A sample flashcard. Give it a flip.")}</p>
        </div>
      </section>
      <section className="features" aria-label={t("A simpler way to learn")}>
        <article><span className="feature-number">01</span><div><h2>{t("Make it yours")}</h2><p>{t("Bring your notes and ideas together in your own flashcard decks.")}</p></div></article>
        <article><span className="feature-number">02</span><div><h2>{t("Find your rhythm")}</h2><p>{t("Build a learning habit, one short practice session at a time.")}</p></div></article>
        <article><span className="feature-number">03</span><div><h2>{t("Keep moving forward")}</h2><p>{t("Make space for curiosity. There is always something new to learn.")}</p></div></article>
      </section>
    </div>
  )
}
