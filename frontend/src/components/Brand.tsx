import { Link } from 'react-router'
import logo from '../assets/icons/logo.svg'

export default function Brand() {
  return (
    <Link className="brand" to="/dashboard" aria-label="FlashMind home">
      <span className="brand-bubble"><img src={logo} alt="" /></span>
      <span className="brand-text">
        <span className="brand-title">FlashMind</span>
        <span className="brand-subtitle">Master your learning</span>
      </span>
    </Link>
  )
}
