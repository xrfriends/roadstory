import { Navigate, Route, Routes } from 'react-router-dom';
import { HomePage } from './pages/HomePage';
import { DevelopmentLibraryPage } from './pages/DevelopmentLibraryPage';
import { CourseDetailPage, CoursesPage, DestinationPage, FreeDrivePage, MobileStoryPage, PlayerPage } from './pages/Pages';

const developmentLibraryEnabled = import.meta.env.DEV && import.meta.env.VITE_DEV_LIBRARY_SYNC === 'true';

export function App() { return <Routes><Route path="/" element={<HomePage />} /><Route path="/free-drive" element={<FreeDrivePage />} /><Route path="/player" element={<PlayerPage />} /><Route path="/destination" element={<DestinationPage />} /><Route path="/courses" element={<CoursesPage />} /><Route path="/courses/:courseId" element={<CourseDetailPage />} /><Route path="/mobile/story/:storyId" element={<MobileStoryPage />} />{developmentLibraryEnabled && <Route path="/dev/library" element={<DevelopmentLibraryPage />} />}<Route path="*" element={<Navigate to="/" replace />} /></Routes>; }

