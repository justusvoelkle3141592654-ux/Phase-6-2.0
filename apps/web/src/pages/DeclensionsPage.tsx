import { useState } from 'react';
import { useI18n } from '../i18n';
import { PageHeader } from '../components/Page';

import { Link } from 'react-router';
import { Camera } from 'lucide-react';

// Placeholder page for Latin declension classes. In a full implementation,
// this would provide a UI to select and practice declension tables.
export function DeclensionsPage() {
  const { m } = useI18n();
  const [selected, setSelected] = useState('');

  const declensions = [
    {
      name: 'puella',
      rows: [
        { case: 'Nominativ', singular: 'puella', plural: 'puellae' },
        { case: 'Genitiv', singular: 'puellae', plural: 'puellārum' },
        { case: 'Akkusativ', singular: 'puellam', plural: 'puellās' },
        { case: 'Dativ', singular: 'puellae', plural: 'puellīs' },
        { case: 'Ablativ', singular: 'puellā', plural: 'puellīs' },
      ],
    },
    {
      name: 'servus',
      rows: [
        { case: 'Nominativ', singular: 'servus', plural: 'servī' },
        { case: 'Genitiv', singular: 'servī', plural: 'servōrum' },
        { case: 'Akkusativ', singular: 'servum', plural: 'servōs' },
        { case: 'Dativ', singular: 'servō', plural: 'servīs' },
        { case: 'Ablativ', singular: 'servō', plural: 'servīs' },
      ],
    },
    {
      name: 'rex',
      rows: [
        { case: 'Nominativ', singular: 'rex', plural: 'rēgēs' },
        { case: 'Genitiv', singular: 'rēgis', plural: 'rēgum' },
        { case: 'Akkusativ', singular: 'rēgem', plural: 'rēgēs' },
        { case: 'Dativ', singular: 'rēgī', plural: 'rēgibus' },
        { case: 'Ablativ', singular: 'rēge', plural: 'rēgibus' },
      ],
    },
  ];

  // Quiz state
  const [quizMode, setQuizMode] = useState(false);
  const [question, setQuestion] = useState<any>(null);
  const [userAnswer, setUserAnswer] = useState('');
  const [feedback, setFeedback] = useState<any>(null);

  const startQuiz = () => {
    // If a noun is selected, quiz only that noun; otherwise quiz all declensions
    const targetDecls = selected ? declensions.filter((d) => d.name === selected) : declensions;
    const pool = targetDecls.flatMap((d) =>
      d.rows.flatMap((row) => [
        {
          decl: d,
          case: row.case,
          number: 'Singular',
          answer: row.singular,
        },
        {
          decl: d,
          case: row.case,
          number: 'Plural',
          answer: row.plural,
        },
      ])
    );
    setRemainingQuestions(pool);
    setQuizMode(true);
    // Start first question after pool is set
    setTimeout(() => nextQuestion(pool), 0);
  };

  const [remainingQuestions, setRemainingQuestions] = useState<any[]>([]);

  const nextQuestion = (questions = remainingQuestions) => {
    if (questions.length === 0) {
      // No more questions – end quiz automatically
      endQuiz();
      return;
    }
    const idx = Math.floor(Math.random() * questions.length);
    const q = questions[idx];
    setQuestion(q);
    setUserAnswer('');
    setFeedback(null);
    // Remove selected question from the pool temporarily; will be re-added if answered incorrectly
    const newPool = questions.filter((_, i) => i !== idx);
    setRemainingQuestions(newPool);
  };

  const checkAnswer = (e: React.FormEvent) => {
    e.preventDefault();
    if (!question) return;
    const isCorrect = userAnswer.trim().toLowerCase() === question.answer.trim().toLowerCase();
    setFeedback({
      correct: isCorrect,
      message: isCorrect ? 'Richtig!' : `Falsch – richtig wäre: ${question.answer}`,
    });
    if (!isCorrect) {
      // Reinsert the question back into the pool for another try
      setRemainingQuestions((prev) => [...prev, question]);
    }
  };

  const endQuiz = () => {
    setQuizMode(false);
    setQuestion(null);
    setFeedback(null);
    setUserAnswer('');
    setRemainingQuestions([]);
  };

  return (
    <>
      <PageHeader title={m.declensions.title} />
      <div className="mb-4">
        <label className="label" htmlFor="decl-select">
          {m.declensions.select}
        </label>
        <select
          id="decl-select"
          className="field"
          value={selected}
          onChange={(e) => {
            setSelected(e.target.value);
            setQuizMode(false);
            setFeedback(null);
          }}
        >
          <option value="">—</option>
          {declensions.map((d) => (
            <option key={d.name} value={d.name}>
              {d.name}
            </option>
          ))}
        </select>
        {selected && !quizMode && (
          <button className="btn btn-primary ml-2" onClick={startQuiz}>
            Quiz starten
          </button>
        )}
        {quizMode && (
          <button className="btn btn-secondary ml-2" onClick={endQuiz}>
            Quiz beenden
          </button>
        )}
      </div>
      {quizMode && question && (
        <div className="mb-4">
          <p className="font-semibold">
            {question.decl.name}: {question.case} ({question.number})
          </p>
          <form onSubmit={checkAnswer}>
            <input
              className="field mr-2"
              placeholder="Deine Antwort"
              value={userAnswer}
              onChange={(e) => setUserAnswer(e.target.value)}
              required
            />
            <button type="submit" className="btn btn-primary">
              Prüfen
            </button>
          </form>
          {feedback && (
            <p className={feedback.correct ? 'text-green-600' : 'text-red-600'}>
              {feedback.message}
            </p>
          )}
          <button className="btn btn-secondary mt-2" onClick={() => nextQuestion()}>
            Nächste Frage
          </button>
        </div>
      )}
      {!quizMode && selected && (
        <p>Wähle "Quiz starten", um das Üben zu beginnen.</p>
      )}
    </>
  );
}
