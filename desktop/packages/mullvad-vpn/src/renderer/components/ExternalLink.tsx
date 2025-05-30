import { useCallback } from 'react';

import { Url } from '../../shared/constants';
import { useAppContext } from '../context';
import { Link, LinkProps } from '../lib/components';

export type ExternalLinkProps = Omit<LinkProps, 'href' | 'as'> & {
  to: Url;
};

function ExternalLink({ to, onClick, ...props }: ExternalLinkProps) {
  const { openUrl } = useAppContext();
  const navigate = useCallback(
    (e: React.MouseEvent<HTMLAnchorElement>) => {
      e.preventDefault();
      if (onClick) {
        onClick(e);
      }
      return openUrl(to);
    },
    [onClick, openUrl, to],
  );
  return <Link href="" onClick={navigate} {...props} />;
}

const ExternalLinkNamespace = Object.assign(ExternalLink, {
  Text: Link.Text,
  Icon: Link.Icon,
});

export { ExternalLinkNamespace as ExternalLink };
