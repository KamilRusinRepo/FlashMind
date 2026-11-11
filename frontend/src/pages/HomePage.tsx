import { useState } from 'react'
import { Link } from 'react-router'

export default function HomePage() {
  const [flipped, setFlipped] = useState(false)

  return (
    <div className="home-page">
      <section className="hero" aria-labelledby="home-title">
        <div className="hero-copy">
          <span className="hero-badge"><span aria-hidden="true">✦</span> SMALL STEPS. BIG PROGRESS.</span>
          <h1 id="home-title">Master your<br /><span>learning.</span></h1>
          <p>Your ideas, your flashcards, your pace. Turn what you want to know into something you remember.</p>
          <div className="hero-actions">
            <Link className="button button-primary" to="/register">Start learning <span aria-hidden="true">→</span></Link>
            <Link className="button button-outline" to="/login">I already have an account</Link>
          </div>
          <p className="hero-footnote">A little practice today. A little more confidence tomorrow.</p>
        </div>
        <div className="hero-preview" id="preview">
          <div className="preview-topline"><span className="preview-dot" /> A MOMENT OF DISCOVERY</div>
          <div className="flashcard-stack">
            <button className={`demo-card${flipped ? ' is-flipped' : ''}`} type="button"
              onClick={() => setFlipped((value) => !value)} aria-pressed={flipped}
              aria-label={flipped ? 'Hide the sample answer' : 'Reveal the sample answer'}>
              <span className="card-category">ENGLISH <span aria-hidden="true">→</span> POLISH</span>
              <span className="card-side">{flipped ? 'THE ANSWER' : 'DO YOU KNOW THIS WORD?'}</span>
              <span className="card-word">{flipped ? 'ciekawość' : 'curiosity'}</span>
              <span className="card-pronunciation">{flipped ? 'A desire to learn something new.' : '/ˌkjʊəriˈɒsəti/'}</span>
              <span className="card-flip-hint"><span aria-hidden="true">↻</span> {flipped ? 'Click to see the question' : 'Click to reveal the answer'}</span>
            </button>
          </div>
          <p className="preview-caption">A sample flashcard. Give it a flip.</p>
        </div>
      </section>
      <section className="features" aria-label="A simpler way to learn">
        <article><span className="feature-number">01</span><div><h2>Make it yours</h2><p>Bring your notes and ideas together in your own flashcard decks.</p></div></article>
        <article><span className="feature-number">02</span><div><h2>Find your rhythm</h2><p>Build a learning habit, one short practice session at a time.</p></div></article>
        <article><span className="feature-number">03</span><div><h2>Keep moving forward</h2><p>Make space for curiosity. There is always something new to learn.</p></div></article>
      </section>
    </div>
  )
}
