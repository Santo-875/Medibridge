"""add username and password_hash to users

Revision ID: 001_auth_users
Revises: 
Create Date: 2026-10-20

"""
from alembic import op
import sqlalchemy as sa

revision = '001_auth_users'
down_revision = None
branch_labels = None
depends_on = None

def upgrade() -> None:
    op.add_column('users', sa.Column('username', sa.String(length=32), nullable=True))
    op.create_index(op.f('ix_users_username'), 'users', ['username'], unique=True)
    op.add_column('users', sa.Column('password_hash', sa.String(length=256), nullable=True))

def downgrade() -> None:
    op.drop_column('users', 'password_hash')
    op.drop_index(op.f('ix_users_username'), table_name='users')
    op.drop_column('users', 'username')
