import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { job } from '../test/fixtures'
import { JobCard } from './JobCard'

describe('JobCard', () => {
  it('shows the job with a link to the posting that opens safely in a new tab', () => {
    render(<JobCard job={job()} />)

    expect(screen.getByRole('heading', { name: 'Backend Engineer' })).toBeInTheDocument()
    expect(screen.getByText('London, UK')).toBeInTheDocument()
    expect(screen.getByText('£50,000 - £60,000')).toBeInTheDocument()
    expect(screen.getByText('via LinkedIn')).toBeInTheDocument()
    expect(screen.getByText('85% match')).toBeInTheDocument()

    const link = screen.getByRole('link', { name: /View posting.*for Backend Engineer \(opens in a new tab\)/ })
    expect(link).toHaveAttribute('href', 'https://example.com/jobs/J-1')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
  })

  it.each(['javascript:alert(document.cookie)', 'data:text/html,<script>alert(1)</script>', 'not a url', null])(
    'renders no link for the URL %j',
    (url) => {
      const { container } = render(<JobCard job={job({ url })} />)

      expect(screen.queryByRole('link')).not.toBeInTheDocument()
      expect(container.querySelector('[href]')).toBeNull()
      expect(screen.getByText('No link available')).toBeInTheDocument()
    },
  )

  it('fills in for details the search could not find', () => {
    render(
      <JobCard
        job={job({
          jobTitle: null,
          location: 'Not specified',
          salaryRange: null,
          datePosted: '',
          lastDateForSubmission: 'not specified',
          source: 'Not specified',
          atsScore: null,
        })}
      />,
    )

    expect(screen.getByRole('heading', { name: 'Untitled role' })).toBeInTheDocument()
    expect(screen.getAllByText('Not specified')).toHaveLength(4)
    expect(screen.getByText('No match score')).toBeInTheDocument()
    expect(screen.queryByText(/^via/)).not.toBeInTheDocument()
  })

  it('shows a score of zero as a score, not as missing', () => {
    render(<JobCard job={job({ atsScore: 0 })} />)

    expect(screen.getByText('0% match')).toBeInTheDocument()
  })
})
