import type { Meta, StoryObj } from '@storybook/react';
import { DriveLimit, EmptyState, ErrorState, LoadingState } from './States';
const meta = { title: 'Common/Data States', component: LoadingState } satisfies Meta<typeof LoadingState>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Loading: Story = {};
export const Empty = { render: () => <EmptyState /> };
export const Error = { render: () => <ErrorState retry={() => undefined} /> };
export const DrivingLimit = { render: () => <DriveLimit /> };
