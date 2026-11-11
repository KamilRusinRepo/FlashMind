import { Link } from 'react-router'

export default function NotFoundPage() {
  return (
    <section className="not-found">
      <span className="eyebrow">404</span>
      <h1>A little off track.</h1>
      <p>We couldn’t find this page. Let’s get you back to the start.</p>
      <Link className="button button-primary" to="/">Back to home</Link>
    </section>
  )
}
